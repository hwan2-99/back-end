package org.kakaoshare.backend.domain.member.service.oauth.detail.kakao;

import org.kakaoshare.backend.domain.member.dto.oauth.logout.OAuthSocialLogoutRequest;
import org.kakaoshare.backend.domain.member.dto.oauth.logout.detail.kakao.request.KakaoLogoutRequest;
import org.kakaoshare.backend.domain.member.service.oauth.OAuthWebClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class KakaoOAuthService implements OAuthWebClientService {
    private final KakaoOAuthRequestProvider kakaoOAuthRequestProvider;
    private final String logoutRequestUrl;
    private final WebClient webClient;

    public KakaoOAuthService(final KakaoOAuthRequestProvider kakaoOAuthRequestProvider,
                             @Value("${spring.security.oauth2.client.other.kakao.logout-url}") final String logoutRequestUrl,
                             final WebClient webClient) {
        this.kakaoOAuthRequestProvider = kakaoOAuthRequestProvider;
        this.logoutRequestUrl = logoutRequestUrl;
        this.webClient = webClient;
    }

    @Override
    public Map<String, Object> getSocialProfile(final ClientRegistration registration,
                                                final String socialToken) {
        return webClient.get()
                .uri(getProfileRequestUri(registration))
                .headers(header -> header.setBearerAuth(socialToken))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    @Override
    public void expireToken(final ClientRegistration registration,
                            final OAuthSocialLogoutRequest oAuthSocialLogoutRequest) {
        final String socialAccessTokenToken = oAuthSocialLogoutRequest.socialAccessToken();
        final String providerId = oAuthSocialLogoutRequest.providerId();
        final KakaoLogoutRequest kakaoLogoutRequest = kakaoOAuthRequestProvider.createKakaoLogoutRequest(providerId);
        webClient.post()
                .uri(logoutRequestUrl)
                .headers(header -> header.setBearerAuth(socialAccessTokenToken))
                .bodyValue(kakaoLogoutRequest)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    private String getProfileRequestUri(final ClientRegistration registration) {
        return registration.getProviderDetails()
                .getUserInfoEndpoint()
                .getUri();
    }
}
