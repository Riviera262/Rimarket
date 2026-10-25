package RiMarket.com.example.demo.shared.security;

import  RiMarket.com.example.demo.module.identity.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

@RequiredArgsConstructor
public class SecurityUser implements UserDetails {

    private final User user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(){
        return user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .toList();
    }

    @Override
    public @Nullable String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    public User getUser(){
        return this.user;
    }

    public UUID getUserId() {
        return user.getId();
    }

    @Override
    public boolean isEnabled(){
        return "ACTIVE".equals(user.getStatus());
    }

    @Override
    public boolean isAccountNonLocked(){
        // If locked is true, then this return false
        return !user.isLocked();
    }

    @Override
    public boolean isAccountNonExpired(){
        // If deleted is true, then this return false
        return !user.isDeleted();
    }

    @Override
    public boolean isCredentialsNonExpired(){
        return true;
    }
}
