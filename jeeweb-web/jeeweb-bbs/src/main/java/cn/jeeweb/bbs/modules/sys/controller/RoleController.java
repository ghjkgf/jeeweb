package cn.jeeweb.bbs.modules.sys.controller;

import cn.jeeweb.bbs.aspectj.annotation.Log;
import cn.jeeweb.bbs.aspectj.enums.LogType;
import cn.jeeweb.bbs.common.bean.ResponseError;
import cn.jeeweb.bbs.modules.posts.entity.PostsColumn;
import cn.jeeweb.bbs.modules.sys.data.SysDatabaseEnum;
import cn.jeeweb.bbs.modules.sys.entity.Menu;
import cn.jeeweb.bbs.modules.sys.entity.RoleMenu;
import cn.jeeweb.bbs.modules.sys.service.IMenuService;
import cn.jeeweb.bbs.modules.sys.service.IRoleMenuService;
import cn.jeeweb.bbs.modules.sys.service.IRoleService;
import cn.jeeweb.bbs.modules.sys.entity.Role;
import cn.jeeweb.bbs.utils.UserUtils;
import cn.jeeweb.common.http.PageResponse;
import cn.jeeweb.common.http.Response;
import cn.jeeweb.common.mvc.annotation.ViewPrefix;
import cn.jeeweb.common.mvc.controller.BaseBeanController;
import cn.jeeweb.common.mybatis.mvc.wrapper.EntityWrapper;
import cn.jeeweb.common.query.annotation.PageableDefaults;
import cn.jeeweb.common.query.data.PropertyPreFilterable;
import cn.jeeweb.common.query.data.Queryable;
import cn.jeeweb.common.query.utils.QueryableConvertUtils;
import cn.jeeweb.common.security.shiro.authz.annotation.RequiresMethodPermissions;
import cn.jeeweb.common.security.shiro.authz.annotation.RequiresPathPermission;
import cn.jeeweb.common.utils.StringUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.serializer.SerializeFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * All rights Reserved, Designed By www.jeeweb.cn
 *
 * @version V1.0
 * @package cn.jeeweb.bbs.modules.sys.controller
 * @title: ?????????????????????
 * @description: ?????????????????????
 * @author: ?????????
 * @date: 2018-09-03 15:10:10
 * @copyright: 2018 www.jeeweb.cn Inc. All rights reserved.
 */

@RestController
@RequestMapping("${jeeweb.admin.url.prefix}/sys/role")
@ViewPrefix("modules/sys/role")
@RequiresPathPermission("sys:role")
@Log(title = "????????????")
public class RoleController extends BaseBeanController<Role> {

	@Autowired
	private IRoleService roleService;
	@Autowired
	private IMenuService menuService;
	@Autowired
	private IRoleMenuService roleMenuService;


	@GetMapping
	@RequiresMethodPermissions("view")
	public ModelAndView list(Model model, HttpServletRequest request, HttpServletResponse response) {
		return displayModelAndView("list");
	}

	/**
	 * ?????????????????????????????????????????????????????????????????????
	 *
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	@RequestMapping(value = "ajaxList", method = { RequestMethod.GET, RequestMethod.POST })
	@Log(logType = LogType.SELECT)
	@RequiresMethodPermissions("list")
	public void ajaxList(Queryable queryable, PropertyPreFilterable propertyPreFilterable, HttpServletRequest request,
						  HttpServletResponse response) throws IOException {
		EntityWrapper<Role> entityWrapper = new EntityWrapper<>(entityClass);
		propertyPreFilterable.addQueryProperty("id");
		// ?????????
		QueryableConvertUtils.convertQueryValueToEntityValue(queryable, entityClass);
		SerializeFilter filter = propertyPreFilterable.constructFilter(entityClass);
		PageResponse<Role> pagejson = new PageResponse<>(roleService.list(queryable,entityWrapper));
		String content = JSON.toJSONString(pagejson, filter);
		StringUtils.printJson(response,content);
	}

	@GetMapping(value = "add")
	public ModelAndView add(Model model, HttpServletRequest request, HttpServletResponse response) {
		model.addAttribute("data", new Role());
		return displayModelAndView ("edit");
	}

	@PostMapping("add")
	@Log(logType = LogType.INSERT)
	@RequiresMethodPermissions("add")
	public Response add(Role entity, BindingResult result,
						   HttpServletRequest request, HttpServletResponse response) {
		// ????????????
		this.checkError(entity,result);
		roleService.insert(entity);
		return Response.ok("????????????");
	}

	@GetMapping(value = "{id}/update")
	public ModelAndView update(@PathVariable("id") String id, Model model, HttpServletRequest request,
							   HttpServletResponse response) {
		Role entity = roleService.selectById(id);
		model.addAttribute("data", entity);
		return displayModelAndView ("edit");
	}

	@PostMapping("{id}/update")
	@Log(logType = LogType.UPDATE)
	@RequiresMethodPermissions("update")
	public Response update(Role entity, BindingResult result,
						   HttpServletRequest request, HttpServletResponse response) {
		// ????????????
		this.checkError(entity,result);
		roleService.insertOrUpdate(entity);
		return Response.ok("????????????");
	}

	@PostMapping("{id}/delete")
	@Log(logType = LogType.DELETE)
	@RequiresMethodPermissions("delete")
	public Response delete(@PathVariable("id") String id) {
		roleService.deleteById(id);
		return Response.ok("????????????");
	}

	@PostMapping("batch/delete")
	@Log(logType = LogType.DELETE)
	@RequiresMethodPermissions("delete")
	public Response batchDelete(@RequestParam("ids") String[] ids) {
		List<String> idList = java.util.Arrays.asList(ids);
		roleService.deleteBatchIds(idList);
		return Response.ok("????????????");
	}

	@GetMapping(value = "authMenu")
	public ModelAndView authMenu(Role role, Model model, HttpServletRequest request, HttpServletResponse response) {
		EntityWrapper<Menu> entityWrapper = new EntityWrapper<Menu>(Menu.class);
		entityWrapper.orderBy("sort", false);
		entityWrapper.eq("t.enabled","1");
		List<Menu> menus = menuService.selectTreeList(entityWrapper);
		List<Menu> selectMenus = menuService.findMenuByRoleId(role.getId());
		model.addAttribute("menus", JSONArray.toJSON(menus));
		model.addAttribute("selectMenus", JSONArray.toJSON(selectMenus));
		model.addAttribute("data", role);
		return displayModelAndView("authMenu");
	}

	@PostMapping(value = "/doAuthMenu")
	@Log(logType = LogType.OTHER,title = "????????????")
	@RequiresMethodPermissions("authMenu")
	public Response doAuthMenu(Role role, HttpServletRequest request, HttpServletResponse response) {
		try {
			String roleId = role.getId();
			String selectIds = request.getParameter("selectIds");
			if (!StringUtils.isEmpty(selectIds)){
				// ??????????????????
				roleMenuService.delete(new EntityWrapper<RoleMenu>(RoleMenu.class).eq("roleId", roleId));
				String[] selectMenus = selectIds.split(",");
				List<RoleMenu> roleMenuList = new ArrayList<RoleMenu>();
				for (String menuId : selectMenus) {
					RoleMenu roleMenu = new RoleMenu();
					roleMenu.setRoleId(roleId);
					roleMenu.setMenuId(menuId);
					roleMenuList.add(roleMenu);
				}
				roleMenuService.insertOrUpdateBatch(roleMenuList);
				UserUtils.clearCache();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return Response.error("??????????????????");
		}
		return Response.ok("??????????????????");
	}
}