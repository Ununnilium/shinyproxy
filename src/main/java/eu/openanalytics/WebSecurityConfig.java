/**
 * Copyright 2016 Open Analytics, Belgium
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.openanalytics;

import java.util.Arrays;

import javax.inject.Inject;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import eu.openanalytics.auth.AuthenticationTypeProxy;
import eu.openanalytics.auth.LogoutHandler;
import eu.openanalytics.services.AppService;
import eu.openanalytics.services.AppService.ShinyApp;
import eu.openanalytics.services.UserService;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	@Inject
	LogoutHandler logoutHandler;

	@Inject
	AppService appService;

	@Inject
	UserService userService;
	
	@Inject
	AuthenticationTypeProxy authType;
	
	@Inject
	AuthenticationEventPublisher eventPublisher;
	
	@Override
	public void configure(WebSecurity web) throws Exception {
		web
			.ignoring().antMatchers("/css/**").and()
			.ignoring().antMatchers("/webjars/**");
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http
			// must disable or handle in proxy
			.csrf().disable()
			// disable X-Frame-Options
			.headers().frameOptions().disable();

		if (authType.get().hasAuthorization()) {
			// Limit access to the app pages
			http.authorizeRequests().antMatchers("/login", "/signin/**", "/signup").permitAll();
			for (ShinyApp app: appService.getApps()) {
				if (app.getGroups() == null || app.getGroups().length == 0) continue;
				String[] appRoles = Arrays.stream(app.getGroups()).map(s -> s.toUpperCase()).toArray(i -> new String[i]);
				http.authorizeRequests().antMatchers("/app/" + app.getName()).hasAnyRole(appRoles);
			}

			// Limit access to the admin pages
			http.authorizeRequests().antMatchers("/admin").hasAnyRole(userService.getAdminRoles());
			
			// All other pages are available to authenticated users
			http.authorizeRequests().anyRequest().fullyAuthenticated();

			http
				.formLogin()
					.loginPage("/login")
					.and()
				.logout()
					.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
					.logoutSuccessHandler(logoutHandler)
					.logoutSuccessUrl("/login");
		}
		
		authType.get().configureHttpSecurity(http);
	}

	@Bean
	public GlobalAuthenticationConfigurerAdapter authenticationConfiguration() {
		return new GlobalAuthenticationConfigurerAdapter() {
			@Override
			public void init(AuthenticationManagerBuilder auth) throws Exception {
				auth.authenticationEventPublisher(eventPublisher);
				authType.get().configureAuthenticationManagerBuilder(auth);
			}
		};
	}
	
	@Bean(name="authenticationManager")
	@Override
	public AuthenticationManager authenticationManagerBean() throws Exception {
		return super.authenticationManagerBean();
	}
}