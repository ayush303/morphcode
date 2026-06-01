package com.morphcode.ai.error;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ResourceNotFoundException extends RuntimeException {
    String resourceName;
    String resourceId;

    public ResourceNotFoundException(String resourceName, String resourceId) {
        super(resourceName + " with id " + resourceId + " not found");
        this.resourceName = resourceName;
        this.resourceId = resourceId;
    }
}
