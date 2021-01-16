package org.datastax.astra;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Map;

import org.datastax.astra.doc.DocumentApiClient;
import org.datastax.astra.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

/**
 * Main interface to interct with Astra API
 *
 * @author Cedrick LUNVEN (@clunven)
 */
public class AstraClient {
    
    /** Environment Variables names you can use to initiate values by convention. */
    public static final String ASTRA_DB_ID       = "ASTRA_DB_ID";
    public static final String ASTRA_DB_REGION   = "ASTRA_DB_REGION";
    public static final String ASTRA_DB_USERNAME = "ASTRA_DB_USERNAME";
    public static final String ASTRA_DB_PASSWORD = "ASTRA_DB_PASSWORD";
    public static final String USERNAME          = "USERNAME";
    public static final String PASSWORD          = "PASSWORD";
    public static final String BASE_URL          = "BASE_URL";
    public static final String TOKEN_TTL         = "TOKEN_TTL";
    
    /** Building Astra base URL. */
    public static final String ASTRA_ENDPOINT_PREFIX = "https://";
    public static final String ASTRA_ENDPOINT_SUFFIX = ".apps.astra.datastax.com/api/rest";
    
    /** Header for authToken. */
    public static final String HEADER_CASSANDRA    = "X-Cassandra-Token";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    
    /** Set a timeout for Http requests. */
    public static final Duration REQUEST_TIMOUT = Duration.ofSeconds(10);
    
    /** Retention period for authToken in seconds, default is 5 MIN */
    public static final Duration DEFAULT_TTL    = Duration.ofSeconds(300);
    
    /** Logger for our Client. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AstraClient.class);
    
    /** This the endPoint to invoke to work with different API(s). */
    private final String baseUrl;
    
    /** Username. */
    private final String username;
    
    /** Password. */
    private final String password;
    
    /** Authentication token, time to live. */
    private final Duration authTokenTtl;
    
    /** Core Java 11 Http Client (limiting dependencies to third-party and ensure portability). **/
    private final HttpClient httpClient = HttpClient.newBuilder()
                .version(Version.HTTP_2)
                .followRedirects(Redirect.NORMAL)
                .build();
    
    /** Object <=> Json marshaller as a Jackson Mapper. */
    private final ObjectMapper objectMapper = new ObjectMapper()
                .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
                .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .setDateFormat(new SimpleDateFormat("dd/MM/yyyy"))
                .setAnnotationIntrospector(new JacksonAnnotationIntrospector());
    
    /** Storing an authentication token to speed up queries. */
    private String authToken;
    
    /** Mark the token update. */
    private long authTokenCreationDate = 0;
    
    /**
     * As immutable object use builder to initiate the object.
     */
    public AstraClient(String baseUrl, String username, String password) {
        this(baseUrl, username, password, DEFAULT_TTL);
    }
    
    /**
     * As immutable object use builder to initiate the object.
     */
    public AstraClient(String baseUrl, String username, String password, Duration ttl) {
        this.baseUrl      = baseUrl;
        this.username     = username;
        this.password     = password;
        this.authTokenTtl =  ttl;
        LOGGER.debug("Initializing Client: BaseUrl={}, username={},passwordLenght={}", 
                baseUrl, username, password.length());
    }
    
    /**
     * As immutable object use builder to initiate the object.
     */
    public AstraClient(String astraDatabaseId, String astraDatabaseRegion, String username, String password) {
        this(new StringBuilder(ASTRA_ENDPOINT_PREFIX)
                        .append(astraDatabaseId)
                        .append("-").append(astraDatabaseRegion)
                        .append(ASTRA_ENDPOINT_SUFFIX)
                        .toString(), username, password, DEFAULT_TTL);
    }
    
    /**
     * As immutable object use builder to initiate the object.
     */
    public AstraClient(String astraDatabaseId, String astraDatabaseRegion, String username, String password, Duration ttl) {
        this(new StringBuilder(ASTRA_ENDPOINT_PREFIX)
                        .append(astraDatabaseId)
                        .append("-").append(astraDatabaseRegion)
                        .append(ASTRA_ENDPOINT_SUFFIX)
                        .toString(), username, password, ttl);
    }

    /**
     * Generate or renew authentication token
     */
    public String getAuthenticationToken() {
        if ((System.currentTimeMillis() - authTokenCreationDate) > 1000 * authTokenTtl.getSeconds()) {
            try {
                // Escaping special chars and preventing JSON injection
                String authRequestBody = new StringBuilder("{")
                    .append("\"username\":")
                    .append(JsonUtils.valueAsJson(username))
                    .append(", \"password\":")
                    .append(JsonUtils.valueAsJson(password))
                    .append("}").toString();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/auth/"))
                        .timeout(REQUEST_TIMOUT)
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(authRequestBody)).build();
                
                HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
                
                if (201 == response.statusCode() || 200 == response.statusCode()) {
                   authToken = (String) objectMapper.readValue(response.body(), Map.class).get("authToken");
                   authTokenCreationDate = System.currentTimeMillis();
                   LOGGER.info("Success Authenticated, token will live for {} second(s).", authTokenTtl.getSeconds());
                } else {
                    throw new IllegalStateException("Cannot generate authentication token HTTP_CODE=" 
                                    + response.statusCode() + ", " + response.body());
                }
                
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot generate authentication token", e);
            }
        }
        return authToken;
    }
    
    /**
     * In test or at initialization we want to test credentials
     */
    public boolean connect() {
        return getAuthenticationToken().length() > 0;
    }
   
    
    /**
     * Getter accessor for attribute 'documentRepository'.
     *
     * @return
     *       current value of 'documentRepository'
     */
    public DocumentApiClient namespace(String namespace) {
        return new DocumentApiClient(this, namespace);
    }

    /**
     * Getter accessor for attribute 'baseUrl'.
     *
     * @return
     *       current value of 'baseUrl'
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Getter accessor for attribute 'httpClient'.
     *
     * @return
     *       current value of 'httpClient'
     */
    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Getter accessor for attribute 'objectMapper'.
     *
     * @return
     *       current value of 'objectMapper'
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
    
    /**
     * Builder Pattern
     */
    public static final AstraClientBuilder builder() {
        return new AstraClientBuilder();
    }
    
    /**
     * Builder pattern
     */
    public static class AstraClientBuilder {
        private String   astraDatabaseId;
        private String   astraDatabaseRegion;
        private String   username;
        private String   password;
        private String   baseUrl;
        private Duration tokenTTL = DEFAULT_TTL;
        
        /**
         * Load defaults from Emvironment variables
         */
        protected AstraClientBuilder() {
            this.astraDatabaseId     = System.getenv(ASTRA_DB_ID);
            this.astraDatabaseRegion = System.getenv(ASTRA_DB_REGION);
            this.username            = System.getenv(ASTRA_DB_USERNAME);
            this.password            = System.getenv(ASTRA_DB_PASSWORD);
             if (null != System.getenv(USERNAME)) {
                this.username = System.getenv(USERNAME);
            }
            if (null != System.getenv(PASSWORD)) {
                this.password = System.getenv(PASSWORD);
            }
            if (null!= System.getenv(BASE_URL)) {
                this.baseUrl = System.getenv(BASE_URL);
            }
            if (null != System.getenv(TOKEN_TTL)) {
                this.tokenTTL = Duration.ofSeconds(Long.parseLong(System.getenv(TOKEN_TTL)));
            }
        }
        
        public AstraClientBuilder astraDatabaseId(String uid) {
            this.astraDatabaseId = uid;
            return this;
        }
        public AstraClientBuilder astraDatabaseRegion(String region) {
            this.astraDatabaseRegion = region;
            return this;
        }
        public AstraClientBuilder username(String username) {
            this.username = username;
            return this;
        }
        public AstraClientBuilder password(String password) {
            this.password = password;
            return this;
        }
        public AstraClientBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        public AstraClientBuilder tokenTtl(Duration tokenTTL) {
            this.tokenTTL = tokenTTL;
            return this;
        }
        
        public AstraClient build() {
            // Username is required
            if (null == username || "".equals(username)) {
                throw new IllegalArgumentException(
                        "Username has not been provided please use username(...)");
            }
            // Password is required
            if (null == password || "".equals(password)) {
                throw new IllegalArgumentException(
                        "Password has not been provided please use password(...)");
            }
            // BaseUrl is not filled we will rely on Astra Key
            if (baseUrl == null) {
                if (null == astraDatabaseId || "".equals(astraDatabaseId)) {
                    throw new IllegalArgumentException(
                            "astraDatabaseId has not been provided please use astraDatabaseId(...) or baseUrl...()");
                }
                if (null == astraDatabaseRegion || "".equals(astraDatabaseRegion)) {
                    throw new IllegalArgumentException(
                            "astraDatabaseRegion has not been provided please use astraDatabaseRegion(...) or baseUrl...()");
                }
                this.baseUrl = new StringBuilder(ASTRA_ENDPOINT_PREFIX)
                        .append(this.astraDatabaseId)
                        .append("-")
                        .append(astraDatabaseRegion)
                        .append(ASTRA_ENDPOINT_SUFFIX)
                        .toString();
            }
            return new AstraClient(baseUrl, username, password, tokenTTL);
        }
    }  
     

}