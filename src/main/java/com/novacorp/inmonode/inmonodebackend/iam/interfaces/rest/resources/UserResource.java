package com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources;

public record UserResource(Long id, String email, String role, String status) {
}
