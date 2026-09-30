package de.conduit.users.dto;

public record ProfileView(String username, String bio, String image, boolean following) {
}
