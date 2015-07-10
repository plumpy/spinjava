package com.netflix.spinnaker.gate.security.anonymous

import com.netflix.spinnaker.gate.security.WebSecurityAugmentor
import com.netflix.spinnaker.gate.services.internal.KatoService
import com.netflix.spinnaker.security.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AnonymousAuthenticationProvider
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter

@ConditionalOnExpression('${onelogin.enabled:false} || ${x509.enabled:false}')
@Configuration
@ConfigurationProperties(prefix = "anonymous")
class AnonymousSecurityConfig implements WebSecurityAugmentor {
  String key = "spinnaker-anonymous"
  String defaultEmail = "anonymous"

  @Autowired
  KatoService katoService

  @Override
  void configure(HttpSecurity http,
                 UserDetailsService userDetailsService,
                 AuthenticationManager authenticationManager) {
    def filter = new AnonymousAuthenticationFilter(
      key, new User(defaultEmail, null, null, ["anonymous"], getAllowedAccounts()), [new SimpleGrantedAuthority("anonymous")]
    )
    http.addFilter(filter)
    http.csrf().disable()
  }

  @Override
  void configure(AuthenticationManagerBuilder auth) {
    auth.authenticationProvider(new AnonymousAuthenticationProvider(key))
  }

  public Collection<String> getAllowedAccounts() {
    return katoService.accounts.findAll { !it.requiredGroupMembership }*.name
  }
}
