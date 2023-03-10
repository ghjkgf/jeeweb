package cn.jeeweb.bbs.modules.front.controller;

import cn.afterturn.easypoi.cache.manager.IFileLoader;
import cn.jeeweb.bbs.common.bean.ResponseError;
import cn.jeeweb.bbs.modules.email.service.IEmailSendService;
import cn.jeeweb.bbs.modules.front.constant.FrontConstant;
import cn.jeeweb.bbs.modules.posts.entity.PostsComment;
import cn.jeeweb.bbs.modules.posts.entity.Posts;
import cn.jeeweb.bbs.modules.posts.service.IPostsCommentService;
import cn.jeeweb.bbs.modules.posts.service.IPostsService;
import cn.jeeweb.bbs.modules.sys.entity.Message;
import cn.jeeweb.bbs.modules.sys.entity.User;
import cn.jeeweb.bbs.modules.sys.service.IMessageService;
import cn.jeeweb.bbs.modules.sys.service.IUserService;
import cn.jeeweb.bbs.security.shiro.credential.RetryLimitHashedCredentialsMatcher;
import cn.jeeweb.bbs.security.shiro.exception.RepeatAuthenticationException;
import cn.jeeweb.bbs.security.shiro.filter.authc.FormAuthenticationFilter;
import cn.jeeweb.bbs.security.shiro.filter.authc.UsernamePasswordToken;
import cn.jeeweb.bbs.utils.LoginLogUtils;
import cn.jeeweb.bbs.utils.SmsVercode;
import cn.jeeweb.bbs.utils.UrlUtils;
import cn.jeeweb.bbs.utils.UserUtils;
import cn.jeeweb.common.http.Response;
import cn.jeeweb.common.mvc.controller.BaseController;
import cn.jeeweb.common.mybatis.mvc.wrapper.EntityWrapper;
import cn.jeeweb.common.utils.*;
import cn.jeeweb.common.utils.jcaptcha.JCaptcha;
import com.baomidou.mybatisplus.plugins.Page;
import com.google.common.collect.Maps;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.ExcessiveAttemptsException;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import sun.misc.Cache;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController("FrontUserController")
@RequestMapping("user")
public class UserController extends BaseController {
	@Autowired
	private RetryLimitHashedCredentialsMatcher retryLimitHashedCredentialsMatcher;
	@Autowired
	private IUserService userService;
	@Autowired
	private IPostsService postsService;
	@Autowired
	private IPostsCommentService postsCommentService;
	@Autowired
	private IMessageService messageService;
	@Autowired
	private IEmailSendService emailSendService;

	/**
	 *  ??????
	 * @param request
	 * @param response
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "/login")
	public Object login(HttpServletRequest request, HttpServletRequest response, Model model) {
		try {
			Subject subject = SecurityUtils.getSubject();
			//????????????????????????????????????????????????
			if (!subject.isAuthenticated()) {
				String username = request.getParameter("username");
				String password = request.getParameter("password");
				//???????????????
				if (request.getMethod().equalsIgnoreCase("post")) {
					//???????????????
					if (!JCaptcha.validateResponse(request, request.getParameter("captcha"))){
						LoginLogUtils.recordFailLoginLog(username,"???????????????????????????");
						return Response.error(ResponseError.NORMAL_ERROR,"???????????????????????????");
					}
				}
				if (request.getMethod().equalsIgnoreCase("get") || !login(subject, username, password, "", request)) {//????????????????????????????????????
					String useruame = WebUtils.getCleanParam(request, FormAuthenticationFilter.DEFAULT_USERNAME_PARAM);
					String exception = (String) request.getAttribute(FormAuthenticationFilter.DEFAULT_ERROR_KEY_ATTRIBUTE_NAME);
					if (request.getMethod().equalsIgnoreCase("post")){
						LoginLogUtils.recordFailLoginLog(username,"????????????????????????????????????");
						return Response.error("???????????????????????????????????????");
					}
					// ??????????????????
					if (ExcessiveAttemptsException.class.getName().equals(exception)
							|| retryLimitHashedCredentialsMatcher.isForceLogin(useruame)) { // ????????????????????????????????????
					}
					if (!ServletUtils.isAjax()) {
						return new ModelAndView("modules/front/user/login");
					}else {
						return Response.error("????????????????????????!");
					}
				}
				Response responseData = Response.ok();
				LoginLogUtils.recordSuccessLoginLog(username,"????????????");
				responseData.put("action", UrlUtils.getRefererUrl());
				return responseData;
			}else{
				return new ModelAndView("redirect:" + UrlUtils.getRefererUrl());
			}
		}
		catch (Exception e) {
		  return Response.error(e.getMessage());
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
		UsernamePasswordToken token= new UsernamePasswordToken(username, password.toCharArray(), rememberMe, host, captcha,false);
		try {
			subject.login(token);
			return true;
		} catch (Exception e) {
			request.setAttribute("error", "????????????:" + e.getClass().getName());
			return false;
		}
	}
	/**
	 * ???????????????
	 *
	 * @param phone ????????????
	 * @param type ?????? register
	 * @return
	 */
	@GetMapping("{type}/vercode")
	public Response verCode(@RequestParam String phone, @PathVariable String type) {
		try {
			//??????????????????????????????encache??????

		}catch (Exception e){

		}
		return Response.ok();
	}
	/**
	 * ??????????????????
	 *
	 * @return
	 */
	@GetMapping("/register")
	public ModelAndView showRegister(Model model) {
		CookieUtils.setCookie(ServletUtils.getResponse(),"vercode_type","register");
		return new ModelAndView("modules/front/user/register");
	}

	/**
	 * ??????????????????
	 *
	 * @return
	 */
	@PostMapping("/register")
	public Object doRegister(@RequestParam("vercode") String vercode,
								   @RequestParam("phone") String phone,
								   @RequestParam("realname") String realname,
								   @RequestParam("pass") String pass,
								   @RequestParam("repass") String repass) {
		try {
			//?????????????????????
			if (!SmsVercode.validateCode(ServletUtils.getRequest(),phone,vercode)){
				return Response.error("????????????????????????");
			}
			if (!pass.equals(repass)){
				return Response.error("?????????????????????????????????");
			}
			//????????????
			if (userService.findByPhone(phone)!=null){
				return Response.error("???????????????????????????????????????????????????");
			}
			//??????
			if (userService.findByRealname(realname)!=null){
				return Response.error("??????????????????????????????????????????");
			}
			User user = new User();
			user.setDefault();
			user.setPhone(phone);
			user.setRealname(realname);
			user.setPassword(pass);
			Random random = new Random();
			String portrait = "/static/images/avatar/"+random.nextInt(13)+".jpg";
			user.setPortrait(portrait);
			user.setCity(AddressUtils.getRealAddressByIP(IpUtils.getIpAddr(ServletUtils.getRequest())));
			userService.register(user);
			Response response = Response.ok("????????????????????????????????????");
			response.put("action","/user/login");
			return response;
		} catch (Exception e) {
			e.printStackTrace();
			return Response.error(e.getMessage());
		}
	}

	/**
	 * ??????????????????
	 *
	 * @return
	 */
	@GetMapping("/forget")
	public ModelAndView showForget(Model model,@RequestParam(value = "type",required = false) String type) {
		if (StringUtils.isEmpty(type)){
			type = "phone";
			CookieUtils.setCookie(ServletUtils.getResponse(),"vercode_type","forget");
		}
		model.addAttribute("type",type);
		String token = ServletUtils.getRequest().getParameter("token");
		if (!StringUtils.isEmpty(token)) {
			User user = (User) CacheUtils.get(token);
			model.addAttribute("token", token);
			model.addAttribute("user", user);
			String key = ServletUtils.getRequest().getParameter("key");
			String value = ServletUtils.getRequest().getParameter("value");
			model.addAttribute("key", key);
			model.addAttribute("value", value);
		}
		return new ModelAndView("modules/front/user/forget");
	}

	/**
	 * ??????????????????
	 *
	 * @return
	 */
	@PostMapping("/forget")
	public Object doForget(
			@RequestParam(value = "type",required = false) String type,
			@RequestParam(value = "vercode",required = false) String vercode,
			@RequestParam(value = "imagecode", required = false) String imagecode,
			@RequestParam(value = "email",required = false) String email,
			@RequestParam(value = "phone",required = false) String phone,
			HttpServletRequest request) {
		try {
			//?????????????????????
			User user = null;
			if (StringUtils.isEmpty(type)) {
				type = "phone";
			}
			if (type.equals("phone")) {
				if (!SmsVercode.validateCode(ServletUtils.getRequest(), phone, vercode)) {
					return Response.error("????????????????????????");
				}
				//????????????
				user = userService.findByPhone(phone);
				if (user == null) {
					return Response.error("????????????????????????????????????????????????");
				}
			}else{
				if (!JCaptcha.validateResponse(request, imagecode)) {
					return Response.error(ResponseError.NORMAL_ERROR, "???????????????????????????");
				}
				//??????
				user = userService.findByEmail(email);
				if (user == null) {
					return Response.error("??????????????????????????????????????????????????????");
				}
			}
			String token = StringUtils.randomUUID();
			CacheUtils.put(token,user);
			if (type.equals("phone")) {
				Response response = Response.ok("?????????????????????????????????????????????????????????");
				response.put("action", "/user/reset?token=" + token + "&key=" + type + "&value=" + phone);
				return response;
			}else{
				Response response = Response.ok("???????????????????????????????????????????????????????????????");
				response.put("action", "/user/forget?type=email");
				sendForgetEmail(user,token);
				return response;
			}

		} catch (Exception e) {
			e.printStackTrace();
			return Response.error(e.getMessage());
		}
	}

	/**
	 * ????????????
	 *
	 * @return
	 */
	@GetMapping("/reset")
	public ModelAndView showResetPassword(Model model,
										  @RequestParam(value = "token") String token,
										  @RequestParam(value = "key") String key,
										  @RequestParam(value = "value") String value) {
		model.addAttribute("showError", 1);
		if (!StringUtils.isEmpty(token)) {
			User user = (User) CacheUtils.get(token);
			if (user != null){
				model.addAttribute("showError", 0);
			}
			model.addAttribute("token", token);
			model.addAttribute("user", user);
			model.addAttribute("key", key);
			model.addAttribute("value", value);
		}
		return new ModelAndView("modules/front/user/reset");
	}

	/**
	 * ????????????
	 *
	 * @return
	 */
	@PostMapping("/reset")
	public Object doResetPassword(@RequestParam(value = "token") String token,
								  @RequestParam("imagecode") String imagecode,
								  @RequestParam("pass") String pass,
								  @RequestParam("repass") String repass,
								  HttpServletRequest request) {
		try {
			User user = (User) CacheUtils.get(token);
			//???????????????
			if (user == null) {
				return Response.error(ResponseError.NORMAL_ERROR, "??????????????????????????????????????????");
			}
			//???????????????
			if (!JCaptcha.validateResponse(request, imagecode)) {
				return Response.error(ResponseError.NORMAL_ERROR, "???????????????????????????");
			}
			if (!pass.equals(repass)){
				return Response.error("?????????????????????????????????");
			}
			//????????????
			userService.changePassword(user.getId(), pass);
			CacheUtils.remove(token);
			Response response = Response.ok("?????????????????????");
			response.put("action","/user/login");
			return response;
		} catch (Exception e) {
			e.printStackTrace();
			return Response.error(e.getMessage());
		}
	}
	/**
	 * ??????
	 * @return
	 */
	@RequestMapping("/logout")
	public ModelAndView logout() {
		try {
			Subject subject = SecurityUtils.getSubject();
			if (subject != null) {
				LoginLogUtils.recordLogoutLoginLog(UserUtils.getUser().getUsername(),"????????????");
				subject.logout();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new ModelAndView("redirect:"+UrlUtils.getRefererUrl());
	}


	/**
	 * ????????????
	 * @return
	 */
	@RequestMapping("/index")
	public ModelAndView index(HttpServletRequest request, HttpServletRequest response, Model model) {
		model.addAttribute("currentUserMenu","index");
		User showUser = userService.selectById(UserUtils.getUser().getId());
		model.addAttribute("showUser",showUser);
		return new ModelAndView("modules/front/user/index");
	}

	/**
	 * ??????
	 * @return
	 */
	@RequestMapping("/set")
	public ModelAndView set(HttpServletRequest request, HttpServletRequest response, Model model) {
		model.addAttribute("currentUserMenu","set");
		return new ModelAndView("modules/front/user/set");
	}

	/**
	 * ??????????????????
	 * @param user
	 * @return
	 */
	@PostMapping(value = "{id}/saveInfo")
	public Response saveInfo(User user) {
		try {
			User oldUser = userService.selectById(user.getId());
			// ??????????????????????????????
			User emailUser = userService.findByEmail(user.getEmail());
			if (emailUser!=null&&!emailUser.getId().equals(user.getId())){
				return Response.error("????????????????????????????????????????????????");
			}
			// ????????????????????????????????????
			if (!user.getEmail().equals(oldUser.getEmail())){
				// ????????????
				user.setEmailActivate("0");
				// ????????????
				sendActivateEmail(user);
			}
			BeanUtils.copyProperties(user,oldUser);
			userService.insertOrUpdate(oldUser);
			String currentUserId = UserUtils.getUser().getId();
			if (currentUserId.equals(user.getId())) {
				UserUtils.clearCache();
			}
		} catch (Exception e) {
			e.printStackTrace();
			Response.error("??????????????????");
		}
		Response response =Response.ok("??????????????????");
		response.put("action","/user/set#info");
		return response;
	}

	/**
	 * ??????????????????
	 * @param user
	 */
	private void sendForgetEmail(User user,String token){
		// ????????????
		Map<String,Object> datas = Maps.newHashMap();
		datas.put("realname",user.getRealname());
		CacheUtils.put(token,user);
		datas.put("email",user.getEmail());
		datas.put("token",token);
		emailSendService.send(user.getEmail(), FrontConstant.EMAIL_FORGET_PASS_TEMPLATE_CODE,datas);
	}

	/**
	 * ??????????????????
	 * @param user
	 */
	private void sendActivateEmail(User user){
		// ????????????
		Map<String,Object> datas = Maps.newHashMap();
		datas.put("realname",user.getRealname());
		String token = StringUtils.randomUUID();
		CacheUtils.put(token,user);
		datas.put("token",token);
		emailSendService.send(user.getEmail(), FrontConstant.EMAIL_ACTIVATE_EMAIL_TEMPLATE_CODE,datas);
	}
	/**
	 * ????????????
	 * @param id
	 * @param password
	 * @return
	 */
	@PostMapping(value = "{id}/changePassword")
	public Response changePassword(@PathVariable("id") String id,
								   @RequestParam("nowPassword") String nowPassword,
								   @RequestParam("password") String password,
								   @RequestParam("rePassword") String rePassword) {
		if (userService.checkPassword(id,nowPassword)){
			if (!password.equals(rePassword)){
				return Response.error("?????????????????????????????????");
			}
			userService.changePassword(id, password);
			try {
				Subject subject = SecurityUtils.getSubject();
				if (subject != null && subject.isAuthenticated()) {
					subject.logout();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}else{
			return Response.error("????????????????????????");
		}
		Response response =Response.ok("??????????????????");
		response.put("action","/user/login");
		return response;
	}

	/**
	 * ????????????
	 * @param id
	 * @param avatar
	 * @param request
	 * @param response
	 * @return
	 */
	@PostMapping(value = {"{id}/avatar","avatar"})
	public Response avatar(@PathVariable(value = "id",required = false) String id,@RequestParam("avatar") String avatar, HttpServletRequest request, HttpServletResponse response) {
		try {
			if (StringUtils.isEmpty(id)){
				id = UserUtils.getUser().getId();
			}
			User user = userService.selectById(id);
			user.setPortrait(avatar);
			userService.insertOrUpdate(user);
			String currentUserId = UserUtils.getUser().getId();
			if (currentUserId.equals(user.getId())) {
				UserUtils.clearCache();
			}
		} catch (Exception e) {
			e.printStackTrace();
			Response.error("??????????????????");
		}
		return Response.ok("??????????????????");
	}

	/**
	 *  ????????????
	 * @return
	 */
	@RequestMapping("/posts")
	public ModelAndView posts(HttpServletRequest request, HttpServletRequest response, Model model) {
		model.addAttribute("currentUserMenu","posts");
		//????????????
		EntityWrapper<Posts> postsEntityWrapper=  new EntityWrapper<>(Posts.class);
		postsEntityWrapper.setTableAlias("p");
		postsEntityWrapper.eq("uid",UserUtils.getUser().getId());
		postsEntityWrapper.orderBy("publishTime", false);
		Page<Posts> postsPageBean = new com.baomidou.mybatisplus.plugins.Page<Posts>(
				1, 10);
		postsPageBean = postsService.selectPostsPage(postsPageBean,postsEntityWrapper);
		model.addAttribute("postsPageBean",postsPageBean);
		return new ModelAndView("modules/front/user/posts");
	}

	/**
	 *  ????????????
	 * @return
	 */
	@RequestMapping("/message")
	public ModelAndView message(HttpServletRequest request, HttpServletRequest response, Model model) {
		model.addAttribute("currentUserMenu","message");
		EntityWrapper<Message> entityWrapper = new EntityWrapper<Message>(Message.class);
		entityWrapper.eq("read",0);
		entityWrapper.eq("readUid",UserUtils.getUser().getId());
		entityWrapper.orderBy("sendDate",false);
		List<Message> messageList = messageService.selectList(entityWrapper);
		model.addAttribute("messageCount",messageList.size());
		model.addAttribute("messageList",messageList);
		return new ModelAndView("modules/front/user/message");
	}

	/**
	 *  ????????????
	 * @return
	 */
	@GetMapping("/activate")
	public ModelAndView activate(Model model,
								 @RequestParam(value = "token",required = false) String token) {
        //????????????
		if (!StringUtils.isEmpty(token)){
			User user = (User)CacheUtils.get(token);
			if (user!=null){
				user = userService.selectById(user.getId());
				user.setEmailActivate("1");
				userService.insertOrUpdate(user);
				model.addAttribute("user",user);
				CacheUtils.remove(token);
				return new ModelAndView("modules/front/user/activate");
			}else{
				model.addAttribute("tips","??????????????????????????????????????????");
				return new ModelAndView("modules/front/other/tips");
			}
		}else{
			User user = userService.selectById(UserUtils.getUser().getId());
			model.addAttribute("user",user);
		}
		return new ModelAndView("modules/front/user/activate");
	}

	/**
	 *  ????????????
	 * @return
	 */
	@PostMapping("/activate")
	public Response activate() {
		User user = userService.selectById(UserUtils.getUser().getId());
		sendActivateEmail(user);
		return Response.ok("???????????????????????????????????????????????????????????????????????????????????????????????????");
	}

	/**
	 *  ????????????
	 * @return
	 */
	@RequestMapping("/forget")
	public ModelAndView forget() {
		return new ModelAndView("modules/front/user/forget");
	}

	/**
	 *  ????????????
	 *
	 * @return
	 */
	@RequestMapping("/product")
	public ModelAndView product(Model model,
								@RequestParam(value = "alias",required = false) String alias) {
		model.addAttribute("currentUserMenu","product");
		return new ModelAndView("modules/front/user/product");
	}

	/**
	 *  ????????????
	 * @return
	 */
	@RequestMapping("/jump")
	public ModelAndView forget(@RequestParam("realname") String realname) {
		EntityWrapper entityWrapper = new EntityWrapper<>();
		entityWrapper.eq("realname",realname);
		User user = userService.selectOne(entityWrapper);
		if (user!=null){
			String redirectUrl = "redirect:/user/home/"+user.getId();
			return new ModelAndView(redirectUrl);
		}
		return new ModelAndView("redirect:/404");
	}

	/**
	 * ????????????
	 * @return
	 */
	@RequestMapping(value = {"/home/{uid}","/home"})
	public ModelAndView home(@PathVariable(value = "uid",required = false) String uid, HttpServletRequest request, HttpServletRequest response, Model model) {
		model.addAttribute("currentUserMenu","home");
		User showUser = null;
		if (StringUtils.isEmpty(uid)){
			uid = UserUtils.getUser().getId();
			showUser = userService.selectById(uid);
		}else{
			showUser = userService.selectById(uid);
		}
		model.addAttribute("showUser",showUser);
		model.addAttribute("currentUserMenu","home");
		//????????????
		EntityWrapper<Posts> postsEntityWrapper=  new EntityWrapper<>(Posts.class);
		postsEntityWrapper.setTableAlias("p");
		postsEntityWrapper.eq("uid",uid);
		postsEntityWrapper.orderBy("publishTime", false);
		Page<Posts> postsPageBean = new com.baomidou.mybatisplus.plugins.Page<Posts>(
				1, 10);
		postsPageBean = postsService.selectPostsPage(postsPageBean,postsEntityWrapper);
		model.addAttribute("postsPageBean",postsPageBean);
		//????????????
		Page<PostsComment> commentPageBean = new com.baomidou.mybatisplus.plugins.Page<PostsComment>(
				1, 5);
		EntityWrapper<PostsComment> commentEntityWrapper=  new EntityWrapper<PostsComment>(PostsComment.class);
		commentEntityWrapper.setTableAlias("c");
		commentEntityWrapper.eq("uid",uid);
		commentEntityWrapper.orderBy("publishTime", false);
		commentPageBean = postsCommentService.selectCommentPage(commentPageBean,commentEntityWrapper,UserUtils.getUser().getId());
		model.addAttribute("commentPageBean",commentPageBean);

		return new ModelAndView("modules/front/user/home");
	}

}
