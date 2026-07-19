package ca.secured;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

// The modern-enablement gate, in-tree: @Secured values may be taken as literal authorities only here.
@EnableMethodSecurity(securedEnabled = true)
class SecurityConfig {
}
