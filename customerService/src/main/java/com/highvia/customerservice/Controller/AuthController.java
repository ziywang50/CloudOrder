package com.highvia.customerservice.Controller;
import com.highvia.common.entity.UserInfo;
import com.highvia.common.enums.UserRole;
import com.highvia.common.security.JwtUtils;
import com.highvia.common.utils.RsaUtils;
import com.highvia.common.dto.Result;
import com.highvia.customerservice.Dto.LoginRequest;
import com.highvia.customerservice.Dto.RegisterDto;
import com.highvia.customerservice.Entity.CustomerEntity;
import com.highvia.customerservice.Repository.CustomerRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final String privateKeyPath;
    private final String publicKeyPath;
    private final String adminSecretKey;

    // Constructor injection
    public AuthController(
            CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder,
            @Value("${jwt.privateKeyPath}") String privateKeyPath,
            @Value("${jwt.publicKeyPath}") String publicKeyPath,
            @Value("${admin.secretKey}") String adminSecretKey) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.privateKeyPath = privateKeyPath;
        this.publicKeyPath = publicKeyPath;
        this.adminSecretKey = adminSecretKey;
    }

    @PostMapping("/signup")
    public Result signup(@RequestBody RegisterDto dto,
                         HttpServletResponse response) throws Exception {  // 加HttpServletResponse
        // 1. Check if exists
        if (customerRepository.existsByEmail(dto.email())) {
            return Result.error("Email already exists");
        }

        // 2. Create customer
        CustomerEntity customer = new CustomerEntity();
        customer.setEmail(dto.email());
        customer.setPassword(passwordEncoder.encode(dto.password()));
        customer.setUsername(dto.username());
        customer.setRole(UserRole.USER);  // Default role for signup
        customer.setEnabled(true);

        customerRepository.save(customer);

        // 3. Generate JWT token
        PrivateKey privateKey = RsaUtils.getPrivateKey(privateKeyPath);
        UserInfo userInfo = new UserInfo(
                customer.getId(),
                customer.getEmail(),
                customer.getUsername(),
                UserRole.USER
        );

        String token = JwtUtils.generateToken(userInfo, privateKey, 30);

        // 4. Set cookie
        Cookie cookie = new Cookie("CS_TOKEN", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(1800);  // 30 minutes
        response.addCookie(cookie);

        // 5. Return success with token
        return Result.success("Registration successful, logged in automatically");
    }

    @PostMapping ("/signup/admin")
    public Result signupAdmin(
            @RequestHeader("X-Admin-Secret") String secret,
            @RequestBody RegisterDto dto,
            HttpServletResponse response
    ) throws Exception {
        if (!adminSecretKey.equals(secret)) {
            return Result.error("Unauthorized");
        }

        // 1. Check if exists
        if (customerRepository.existsByEmail(dto.email())) {
            return Result.error("Email already exists");
        }

        // 2. Create customer
        CustomerEntity customer = new CustomerEntity();
        customer.setEmail(dto.email());
        customer.setPassword(passwordEncoder.encode(dto.password()));
        customer.setUsername(dto.username());
        customer.setRole(UserRole.ADMIN);  // Default role for signup
        customer.setEnabled(true);

        customerRepository.save(customer);

        // 3. Generate JWT token
        PrivateKey privateKey = RsaUtils.getPrivateKey(privateKeyPath);
        UserInfo userInfo = new UserInfo(
                customer.getId(),
                customer.getEmail(),
                customer.getUsername(),
                UserRole.ADMIN
        );

        String token = JwtUtils.generateToken(userInfo, privateKey, 30);  // 30 min, you're right!

        // 4. Set cookie
        Cookie cookie = new Cookie("CS_TOKEN", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(1800);  // 30 minutes
        response.addCookie(cookie);

        // 5. Return success with token
        return Result.success("Registration successful, logged in automatically");
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request,
                        HttpServletResponse response) throws Exception {
        //find customer my email
        CustomerEntity customer = customerRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User not found"));;
        if (!passwordEncoder.matches(request.password(), customer.getPassword())) {
            throw new RuntimeException("Invalid password");
        }
        //create jwt token
        PrivateKey privateKey = RsaUtils.getPrivateKey(privateKeyPath);
        UserInfo userInfo = new UserInfo(
                customer.getId(),
                customer.getEmail(),
                customer.getUsername(),
                customer.getRole()
        );
        //generate access token
        String accessToken = JwtUtils.generateToken(userInfo, privateKey, 30);
        //generate refresh token
        String refreshToken = JwtUtils.generateToken(userInfo, privateKey, 10080);  //7 days

        // 4. Set cookie
        response.addCookie(createCookie("CS_TOKEN", accessToken, 1800));
        response.addCookie(createCookie("CS_REFRESH_TOKEN", refreshToken, 604800));
        return Result.success(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken
        ));
    }

    @PostMapping("/refresh")
    public Result refresh(@CookieValue("CS_REFRESH_TOKEN") String refreshToken,
                          HttpServletResponse response) throws Exception {
        // Check refresh token
        PublicKey publicKey = RsaUtils.getPublicKey(publicKeyPath);
        UserInfo userInfo = JwtUtils.getInfoFromToken(refreshToken, publicKey);

        // Generate new access token
        PrivateKey privateKey = RsaUtils.getPrivateKey(privateKeyPath);
        String newAccessToken = JwtUtils.generateToken(userInfo, privateKey, 30);

        response.addCookie(createCookie("CS_TOKEN", newAccessToken, 1800));
        return Result.success("Token refreshed");
    }

    // Helper
    private Cookie createCookie(String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        return cookie;
    }

}
