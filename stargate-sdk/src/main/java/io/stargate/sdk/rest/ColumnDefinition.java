package io.stargate.sdk.rest;

public class ColumnDefinition {
    
    String name;
    
    TypeDefinition typeDefinition;
    
    String isStatic;
    
    
    public enum TypeDefinition {
        text, date;
    }
}
