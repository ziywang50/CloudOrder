package com.highvia.common.security;

import com.highvia.common.entity.UserInfo;
import com.highvia.common.enums.UserRole;
import com.highvia.common.utils.JwtConstants;
import com.highvia.common.utils.ObjectUtils;
import com.highvia.common.utils.RsaUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.security.PrivateKey;
import java.security.PublicKey;

@Slf4j
public class JwtUtils {
    /**
     * Generate Token using RSA key
     *@param userInfo
     *@param privateKey
     *@param expireMinutes
     *@return
     *@throws Exception
     */

    public static String generateToken(UserInfo userInfo, PrivateKey privateKey, int expireMinutes) throws Exception {
        return Jwts.builder()
                .claim(JwtConstants.JWT_KEY_ID, userInfo.id())
                .claim(JwtConstants.JWT_KEY_EMAIL, userInfo.email())
                .claim(JwtConstants.JWT_KEY_USER_NAME, userInfo.username())
                .claim(JwtConstants.JWT_KEY_ROLE, userInfo.role().name())
                .expiration(new Date(System.currentTimeMillis() + expireMinutes * 60 * 1000L))
                .signWith(privateKey)
                .compact();
    }

    /**
     * Generate Token using RSA key
     *@param userInfo
     *@param privateKey, with data type byte[]
     *@param expireMinutes
     *@return
     *@throws Exception
     */

    public static String generateToken(UserInfo userInfo, byte[] privateKey, int expireMinutes) throws Exception {
        return generateToken(userInfo, RsaUtils.getPrivateKey(privateKey), expireMinutes);
    }

    /** Get Information from public token and validate JWT.
     * @param token String jwt token
     * @param publicKey RSA public key
     */
    public static UserInfo getInfoFromToken(String token, PublicKey publicKey) throws Exception {
        Jws<Claims> claimsJws = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
        Claims body = claimsJws.getPayload();
        String roleStr = ObjectUtils.toString(body.get(JwtConstants.JWT_KEY_ROLE));
        UserRole role;
        try {
            role = UserRole.valueOf(roleStr);
        } catch (Exception e) {
            log.warn("Invalid role '{}' in JWT, defaulting to USER. Token claims: {}", roleStr, body, e);
            role = UserRole.USER;
        }
        return new UserInfo(
                ObjectUtils.toLong(body.get(JwtConstants.JWT_KEY_ID)),
                ObjectUtils.toString(body.get(JwtConstants.JWT_KEY_EMAIL)),
                ObjectUtils.toString(body.get(JwtConstants.JWT_KEY_USER_NAME)),
                role
        );
    }

    /** Parse Information from public token and validate JWT.
     * @param token String jwt token
     * @param publicKey RSA public key, with data type byte[]
     */
    public static UserInfo getInfoFromToken(String token,
                                            byte[] publicKey) throws Exception {
        return getInfoFromToken(token, RsaUtils.getPublicKey(publicKey));
    }

}
