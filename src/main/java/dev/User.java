package dev;

public class User {
    public String username;
    public String email;
    public String password;
    public String hwid;
    public String currentParam = "3_mode";
    public String tokenCode = "public";
    public String role = "USER";
    public String registerIp;

    public User() {

    }

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getHwid() { return hwid; }
    public void setHwid(String hwid) { this.hwid = hwid; }

    public String getCurrentParam() { return currentParam; }
    public void setCurrentParam(String currentParam) { this.currentParam = currentParam; }

    public String getTokenCode() { return tokenCode; }
    public void setTokenCode(String tokenCode) { this.tokenCode = tokenCode; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getRegisterIp() { return registerIp; }
    public void setRegisterIp(String registerIp) { this.registerIp = registerIp; }
}