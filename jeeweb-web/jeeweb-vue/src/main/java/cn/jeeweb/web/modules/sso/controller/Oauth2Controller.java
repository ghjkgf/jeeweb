package cn.jeeweb.web.modules.sso.controller;

import cn.jeeweb.common.utils.IpUtils;
import cn.jeeweb.common.utils.StringUtils;
import cn.jeeweb.web.security.shiro.exception.RepeatAuthenticationException;
import cn.jeeweb.web.security.shiro.filter.authc.UsernamePasswordToken;
import cn.jeeweb.web.security.shiro.realm.UserRealm;
import cn.jeeweb.web.utils.LoginLogUtils;
import cn.jeeweb.web.utils.UserUtils;
import cn.jeeweb.web.common.response.ResponseError;
import cn.jeeweb.web.config.autoconfigure.ShiroConfigProperties;
import cn.jeeweb.web.modules.sso.service.IOAuthService;
import cn.jeeweb.web.utils.JWTHelper;
import cn.jeeweb.web.utils.ResponseUtils;
import org.apache.oltu.oauth2.as.issuer.MD5Generator;
import org.apache.oltu.oauth2.as.issuer.OAuthIssuer;
import org.apache.oltu.oauth2.as.issuer.OAuthIssuerImpl;
import org.apache.oltu.oauth2.as.request.OAuthAuthzRequest;
import org.apache.oltu.oauth2.as.request.OAuthTokenRequest;
import org.apache.oltu.oauth2.as.response.OAuthASResponse;
import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.OAuthResponse;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.apache.oltu.oauth2.common.message.types.ParameterStyle;
import org.apache.oltu.oauth2.common.message.types.ResponseType;
import org.apache.oltu.oauth2.common.utils.OAuthUtils;
import org.apache.oltu.oauth2.rs.request.OAuthAccessResourceRequest;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.ExcessiveAttemptsException;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.authc.FormAuthenticationFilter;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * All rights Reserved, Designed By www.gzst.gov.cn
 *
 * @version V1.0
 * @package cn.gov.gzst.api.web.controller
 * @title:
 * @description: Oauth2.0????????????
 * @author: ?????????
 * @date: 2018/1/8 15:56
 * @copyright: 2017 www.gzst.gov.cn Inc. All rights reserved.
 */
@Controller
@RequestMapping("/sso/oauth2")
public class Oauth2Controller {

    @Autowired
    private IOAuthService oAuthService;

    @Autowired
    private ShiroConfigProperties shiroConfigProperties;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * ????????????
     *
     * @param model
     * @param request
     * @return
     * @throws URISyntaxException
     * @throws OAuthSystemException
     */
    @RequestMapping("/authorize")
    public Object authorize(Model model, HttpServletRequest request) throws URISyntaxException, OAuthSystemException {
        try {
            //??????OAuth ????????????
            OAuthAuthzRequest oauthRequest = new OAuthAuthzRequest(request);

            //????????????????????????id????????????
            if (!oAuthService.checkClientId(oauthRequest.getClientId())) {
                return ResponseUtils.getErrResponse(HttpServletResponse.SC_BAD_REQUEST, ResponseError.INVALID_CLIENT);
            }
            Subject subject = SecurityUtils.getSubject();
            //????????????????????????????????????????????????
            if(!subject.isAuthenticated()) {
                String username=request.getParameter("username");
                String password=request.getParameter("password");
                if(request.getMethod().equalsIgnoreCase("get")||!login(subject, username,password,"",request)) {//????????????????????????????????????
                    // ??????????????????,????????????????????????
                    String url = "redirect:" + shiroConfigProperties.getLoginUrl()+"?client_id="+oauthRequest.getClientId() +"&response_type="+oauthRequest.getResponseType()+"&redirect_uri="+oauthRequest.getRedirectURI();
                    return new ModelAndView(url);
                }
            }

            // ???????????????????????????????????????????????????
            UserRealm.Principal principal = UserUtils.getPrincipal(); // ?????????????????????????????????????????????
            //???????????????
            String authorizationCode = null;
            //responseType???????????????CODE???????????????TOKEN
            String responseType = oauthRequest.getParam(OAuth.OAUTH_RESPONSE_TYPE);
            if (responseType.equals(ResponseType.CODE
                    .toString())) {
                OAuthIssuerImpl oauthIssuerImpl = new OAuthIssuerImpl(new MD5Generator());
                authorizationCode = oauthIssuerImpl.authorizationCode();
                oAuthService.addAuthCode(authorizationCode, principal);
            }

            //??????OAuth????????????
            OAuthASResponse.OAuthAuthorizationResponseBuilder builder =
                    OAuthASResponse.authorizationResponse(request, HttpServletResponse.SC_FOUND);
            //???????????????
            builder.setCode(authorizationCode);
            String loginSource=request.getParameter("login_source");
            if (!StringUtils.isEmpty(loginSource)&&loginSource.equals("client")) {
                //?????????????????????????????????
                String redirectURI = oauthRequest.getParam(OAuth.OAUTH_REDIRECT_URI);
                //????????????
                final OAuthResponse response = builder.location(redirectURI).buildJSONMessage();
                return new ResponseEntity(response.getBody(), HttpStatus.valueOf(HttpServletResponse.SC_OK));
            }else{
                //?????????????????????????????????
                String redirectURI = oauthRequest.getParam(OAuth.OAUTH_REDIRECT_URI);
                //????????????
                final OAuthResponse response = builder.location(redirectURI).buildQueryMessage();

                //??????OAuthResponse??????ResponseEntity??????
                HttpHeaders headers = new HttpHeaders();
                headers.setLocation(new URI(response.getLocationUri()));
                return new ResponseEntity(headers, HttpStatus.valueOf(response.getResponseStatus()));
            }
        } catch (OAuthProblemException e) {
            //????????????
            String redirectUri = e.getRedirectUri();
            String loginSource=request.getParameter("login_source");
            if (!StringUtils.isEmpty(loginSource)&&loginSource.equals("client")) {
                if (OAuthUtils.isEmpty(redirectUri)) {
                    //???????????????????????????redirectUri????????????
                    return ResponseUtils.getErrResponse(HttpStatus.NOT_FOUND.value(), ResponseError.NOT_FOUND_REDIRECT_URI);
                }

                //?????????????????????????error=???
                final OAuthResponse response =
                        OAuthASResponse.errorResponse(HttpServletResponse.SC_FOUND)
                                .error(e).location(redirectUri).buildJSONMessage();
                return new ResponseEntity(response.getBody(), HttpStatus.valueOf(response.getResponseStatus()));
            }else{
                //?????????????????????????error=???
                final OAuthResponse response =
                        OAuthASResponse.errorResponse(HttpServletResponse.SC_FOUND)
                                .error(e).location(redirectUri).buildQueryMessage();
                HttpHeaders headers = new HttpHeaders();
                headers.setLocation(new URI(response.getLocationUri()));
                return new ResponseEntity(headers, HttpStatus.valueOf(response.getResponseStatus()));
            }
        }
    }

    /**
     * ????????????
     *
     * @param subject
     * @param request
     * @return
     */
    private boolean login(Subject subject,String username,String password,String captcha,HttpServletRequest request) {
        if(StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
            return false;
        }
        boolean rememberMe = true;
        String host = IpUtils.getIpAddr((HttpServletRequest) request);
        UsernamePasswordToken token= new UsernamePasswordToken(username, password.toCharArray(), rememberMe, host, captcha);
        try {
            subject.login(token);
            LoginLogUtils.recordSuccessLoginLog(UserUtils.getUser().getUsername(),"????????????");
            return true;
        } catch (AuthenticationException e) {
            request.setAttribute("error", "????????????????????????");
            LoginLogUtils.recordFailLoginLog(username,"????????????????????????");
            return false;
        } catch (Exception e) {
            request.setAttribute("error", "????????????:" + e.getClass().getName());
            LoginLogUtils.recordFailLoginLog(username,"????????????:" + e.getClass().getName());
            return false;
        }
    }


    @RequestMapping("/access_token")
    @ResponseBody
    public HttpEntity accessToken(HttpServletRequest request)
            throws URISyntaxException, OAuthSystemException {

        try {
            //??????OAuth??????
            OAuthTokenRequest oauthRequest = new OAuthTokenRequest(request);

            //????????????????????????id????????????
            if (!oAuthService.checkClientId(oauthRequest.getClientId())) {
                return ResponseUtils.getErrResponse(HttpServletResponse.SC_BAD_REQUEST, ResponseError.INVALID_CLIENT);
            }

            // ?????????????????????KEY????????????
            if (!oAuthService.checkClientSecret(oauthRequest.getClientSecret())) {
                return ResponseUtils.getErrResponse(HttpServletResponse.SC_UNAUTHORIZED, ResponseError.INVALID_CLIENT_SECRET);
            }

            String authCode = "";
            // ????????????????????????????????????AUTHORIZATION_CODE????????????????????????PASSWORD???REFRESH_TOKEN
            if (oauthRequest.getParam(OAuth.OAUTH_GRANT_TYPE).equals(GrantType.AUTHORIZATION_CODE.toString())) {
                authCode=oauthRequest.getParam(OAuth.OAUTH_CODE);
                if (!oAuthService.checkAuthCode(authCode)) {
                    return ResponseUtils.getErrResponse(HttpServletResponse.SC_UNAUTHORIZED, ResponseError.INVALID_AUTH_CODE);
                }
            }  else if (oauthRequest.getParam(OAuth.OAUTH_GRANT_TYPE).equals(GrantType.PASSWORD.toString())) { //????????????
                //?????????????????????????????????
                Subject subject = SecurityUtils.getSubject();
                String username = oauthRequest.getUsername();
                String password = oauthRequest.getPassword();
                //????????????????????????????????????????????????
                if(!subject.isAuthenticated()&&!login(subject, username, password, "", request)) {
                    String error = (String) request.getAttribute("error");
                    return ResponseUtils.getErrResponse(HttpServletResponse.SC_UNAUTHORIZED, ResponseError.MSG_LOGIN_FAILE, error);
                } else {
                    UserRealm.Principal principal = UserUtils.getPrincipal(); // ?????????????????????????????????????????????
                    OAuthIssuerImpl oauthIssuerImpl = new OAuthIssuerImpl(new MD5Generator());
                    authCode = oauthIssuerImpl.authorizationCode();
                    oAuthService.addAuthCode(authCode, principal);
                }
            }

            //??????Access Token
            UserRealm.Principal principal=oAuthService.getPrincipalByAuthCode(authCode);
            Map<String,String> dataMap=new HashMap<String,String>();
            dataMap.put("id",principal.getId());
            dataMap.put("username",principal.getUsername());
            dataMap.put("realname",principal.getRealname());
            final String accessToken = JWTHelper.sign(dataMap, shiroConfigProperties.getJwtTokenSecret(),Long.parseLong(oAuthService.getExpireIn()+""));
            oAuthService.addAccessToken(accessToken, oAuthService.getPrincipalByAuthCode(authCode));

            //??????Refresh Token
            OAuthIssuer oauthIssuerImpl = new OAuthIssuerImpl(new MD5Generator());
            final String refreshToken = oauthIssuerImpl.refreshToken();
            oAuthService.addRefreshToken(refreshToken, oAuthService.getPrincipalByAuthCode(authCode));

            //??????OAuth??????
            OAuthResponse response = OAuthASResponse
                    .tokenResponse(HttpServletResponse.SC_OK)
                    .setAccessToken(accessToken)
                    .setRefreshToken(refreshToken)
                    // .setParam("openid",UserUtils.getPrincipal().getId())
                    .setExpiresIn(String.valueOf(oAuthService.getExpireIn()))
                    .buildJSONMessage();

            //??????OAuthResponse??????ResponseEntity
            return new ResponseEntity(response.getBody(), HttpStatus.valueOf(response.getResponseStatus()));
        } catch (OAuthProblemException e) {
            //??????????????????
            return ResponseUtils.getErrResponse(HttpServletResponse.SC_BAD_REQUEST, ResponseError.INVALID_AUTH_CODE,e.getDescription());
        }
    }


    @RequestMapping("/refresh_token")
    @ResponseBody
    public HttpEntity refreshToken(HttpServletRequest request)
            throws URISyntaxException, OAuthSystemException {

        try {
            //??????OAuth??????
            OAuthTokenRequest oauthRequest = new OAuthTokenRequest(request);

            //????????????????????????id????????????
            if (!oAuthService.checkClientId(oauthRequest.getClientId())) {
                return ResponseUtils.getErrResponse(HttpServletResponse.SC_BAD_REQUEST, ResponseError.INVALID_CLIENT);
            }

            String refreshToken = oauthRequest.getParam(OAuth.OAUTH_REFRESH_TOKEN);
            // ????????????????????????????????????AUTHORIZATION_CODE????????????????????????PASSWORD???REFRESH_TOKEN
            if (oauthRequest.getParam(OAuth.OAUTH_GRANT_TYPE).equals(GrantType.REFRESH_TOKEN.toString())) {
                if (!oAuthService.checkRefreshToken(refreshToken)) {
                    return ResponseUtils.getErrResponse(HttpServletResponse.SC_UNAUTHORIZED, ResponseError.INVALID_REFRESH_TOKEN);
                }
            }

            //??????Access Token
            UserRealm.Principal principal=oAuthService.getPrincipalByRefreshToken(refreshToken);
            Map<String,String> dataMap=new HashMap<String,String>();
            dataMap.put("id",principal.getId());
            dataMap.put("username",principal.getUsername());
            dataMap.put("realname",principal.getRealname());
            final String accessToken = JWTHelper.sign(dataMap, shiroConfigProperties.getJwtTokenSecret(),Long.parseLong(oAuthService.getExpireIn()+""));
            oAuthService.addAccessToken(accessToken, oAuthService.getPrincipalByRefreshToken(refreshToken));

            //??????OAuth??????
            OAuthResponse response = OAuthASResponse
                    .tokenResponse(HttpServletResponse.SC_OK)
                    .setAccessToken(accessToken)
                    .setRefreshToken(refreshToken)
                    .setExpiresIn(String.valueOf(oAuthService.getExpireIn()))
                    .buildJSONMessage();

            //??????OAuthResponse??????ResponseEntity
            return new ResponseEntity(response.getBody(), HttpStatus.valueOf(response.getResponseStatus()));
        } catch (OAuthProblemException e) {
            //??????????????????
            return ResponseUtils.getErrResponse(HttpServletResponse.SC_BAD_REQUEST, ResponseError.INVALID_REFRESH_TOKEN,e.getDescription());
        }
    }

    /**
     * ??????TOKEN
     * @param request
     * @return
     * @throws URISyntaxException
     * @throws OAuthSystemException
     */
    @RequestMapping("/check_token")
    @ResponseBody
    public HttpEntity checkToken(HttpServletRequest request)
            throws URISyntaxException, OAuthSystemException {
        //try {
        //??????OAuth????????????
        //OAuthAccessResourceRequest oauthRequest = new OAuthAccessResourceRequest(request, ParameterStyle.QUERY);

        //??????Access Token
        String accessToken = request.getHeader("access_token");
        //??????Access Token
        if (!oAuthService.checkAccessToken(accessToken)) {
            // ???????????????/???????????????????????????????????????????????????
            return ResponseUtils.getErrResponse(HttpServletResponse.SC_BAD_REQUEST, ResponseError.INVALID_ACCESS_TOKEN);
        }
        return ResponseUtils.getErrResponse(HttpServletResponse.SC_OK, ResponseError.OK);
       /* } catch (OAuthProblemException e) {
            //??????????????????
            return ResponseUtils.getErrResponse(HttpServletResponse.SC_BAD_REQUEST, ResponseError.INVALID_ACCESS_TOKEN,e.getDescription());
        }*/
    }

    /**
     * ??????TOKEN
     * @param request
     * @return
     * @throws URISyntaxException
     * @throws OAuthSystemException
     */
    @RequestMapping("/revoke_token")
    @ResponseBody
    public HttpEntity revokeToken(HttpServletRequest request)
            throws URISyntaxException, OAuthSystemException {
        //try {
            //??????OAuth????????????
            //OAuthAccessResourceRequest oauthRequest = new OAuthAccessResourceRequest(request, ParameterStyle.QUERY);
        //??????Access Token
        String accessToken = request.getHeader("access_token");
        //??????Access Token
        oAuthService.revokeToken(accessToken);
        LoginLogUtils.recordLogoutLoginLog(UserUtils.getUser().getUsername(),"????????????");
        return ResponseUtils.getErrResponse(HttpServletResponse.SC_OK, ResponseError.OK);
        /*} catch (OAuthProblemException e) {
            //??????????????????
            return ResponseUtils.getErrResponse(HttpServletResponse.SC_BAD_REQUEST, ResponseError.INVALID_ACCESS_TOKEN, e.getDescription());
        }*/
    }
}