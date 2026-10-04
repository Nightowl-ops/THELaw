package com.veritasvault.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
@Component


/// in summay the oneperrequest is used to garente that no matter how many times spring boot bounces the request around internally you JWT decoding and database lookup will excute onlu once per real http request
/// when it sees it it check form the stamp oh this has already been verified 2 ,illisecnds before

public class JwtRequestFilter extends OncePerRequestFilter {
// simply to know where did the error come form and when did it happen
    private static final Logger log = LoggerFactory.getLogger(JwtRequestFilter.class);

    private final JwtUtils jwtUtils;
    private final MyUserDetailsService userDetailsService;

    public JwtRequestFilter(JwtUtils jwtUtils, MyUserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(

            // this is the incoming http request coming from postman and the frontend
// non null is a safeguard anotation warining the complier that this object must never be null
            @NonNull HttpServletRequest request,
// the outpt travilling back to postman this is where the status  codes 200 or 401 comes from
            @NonNull HttpServletResponse response,
            // the list of all security guard for this to confiemed
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 1. Check if the Authorization header is missing or doesn't start with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract the token substring (skip "Bearer ")
        jwt = authHeader.substring(7);

        try {
            userEmail = jwtUtils.extractUsername(jwt);

            // 3. If email is valid and user is not already authenticated in this context
            // so you dont need to recheck again for every entry you do
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                // 4. Validate token against the database user

                if (jwtUtils.validateToken(jwt, userDetails)) {

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()// our badge
                    );

                    ///A built-in Spring helper that reads two basic network facts from that request:
                    ///
                    /// The user's IP address (e.g., 192.168.1.15).
                    ///
                    /// The Session ID (if any).
                /// so if an attecker enters you can get there ip address
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    //setDetails is used to add it to the back of the badge
                    // 5. Store authentication in the security context
                    SecurityContextHolder.getContext().setAuthentication(authToken);// place the padge on spring desk  so every  controller knows he is offically logged  in for  this request

                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        // 6. Continue the filter chain
        filterChain.doFilter(request, response);
    }
}