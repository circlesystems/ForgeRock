package ai.circle.models;

public class AuthorizeRequest {
    private String token;

    public AuthorizeRequest() {
    }

    public AuthorizeRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
