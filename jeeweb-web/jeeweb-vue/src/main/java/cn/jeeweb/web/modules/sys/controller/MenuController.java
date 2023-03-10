package cn.jeeweb.web.modules.sys.controller;

import cn.jeeweb.common.utils.ArrayUtils;
import cn.jeeweb.common.utils.FastJsonUtils;
import cn.jeeweb.web.aspectj.annotation.Log;
import cn.jeeweb.web.aspectj.enums.LogType;
import cn.jeeweb.web.common.helper.VueTreeHelper;
import cn.jeeweb.web.modules.sys.service.IMenuService;
import cn.jeeweb.web.modules.sys.entity.Menu;
import cn.jeeweb.common.http.PageResponse;
import cn.jeeweb.common.http.Response;
import cn.jeeweb.common.mvc.annotation.ViewPrefix;
import cn.jeeweb.common.mvc.controller.BaseBeanController;
import cn.jeeweb.common.mvc.entity.tree.BootstrapTreeHelper;
import cn.jeeweb.common.mvc.entity.tree.BootstrapTreeNode;
import cn.jeeweb.common.mvc.entity.tree.TreeSortUtil;
import cn.jeeweb.common.mybatis.mvc.wrapper.EntityWrapper;
import cn.jeeweb.common.query.annotation.PageableDefaults;
import cn.jeeweb.common.query.data.PropertyPreFilterable;
import cn.jeeweb.common.query.data.QueryPropertyPreFilter;
import cn.jeeweb.common.query.data.Queryable;
import cn.jeeweb.common.query.utils.QueryableConvertUtils;
import cn.jeeweb.common.security.shiro.authz.annotation.RequiresMethodPermissions;
import cn.jeeweb.common.security.shiro.authz.annotation.RequiresPathPermission;
import cn.jeeweb.common.utils.ObjectUtils;
import cn.jeeweb.common.utils.StringUtils;
import cn.jeeweb.web.utils.MenuTreeHelper;
import cn.jeeweb.web.utils.UserUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializeFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/sys/menu")
@ViewPrefix("modules/sys/menu")
@RequiresPathPermission("sys:menu")
@Log(title = "????????????")
public class MenuController extends BaseBeanController<Menu> {

    @Autowired
    private IMenuService menuService;

    /**
     * ?????????????????????????????????????????????????????????????????????
     *
     * @param request
     * @throws IOException
     */
    @GetMapping(value = "list")
    @Log(logType = LogType.SELECT)
    @RequiresMethodPermissions("list")
    public void list(HttpServletRequest request) throws IOException {
        EntityWrapper<Menu> entityWrapper = new EntityWrapper<Menu>(entityClass);
        entityWrapper.setTableAlias("t");
        //????????????
        String keyword = request.getParameter("keyword");
        if (!StringUtils.isEmpty(keyword)){
            entityWrapper.like("name",keyword);
        }
        entityWrapper.orderBy("sort");
        List<Menu> treeNodeList = menuService.selectTreeList(entityWrapper);
        List<VueTreeHelper.VueTreeNode> vueTreeNodes = VueTreeHelper.create().sort(treeNodeList);
        FastJsonUtils.print(vueTreeNodes);
    }

    @PostMapping("add")
    @Log(logType = LogType.INSERT)
    @RequiresMethodPermissions("add")
    public Response add(Menu entity, BindingResult result,
                        HttpServletRequest request, HttpServletResponse response) {
        // ????????????
        this.checkError(entity,result);
        menuService.insert(entity);
        return Response.ok("????????????");
    }

    @PostMapping("{id}/update")
    @Log(logType = LogType.UPDATE)
    @RequiresMethodPermissions("update")
    public Response update(Menu entity, BindingResult result,
                           HttpServletRequest request, HttpServletResponse response) {
        // ????????????
        this.checkError(entity,result);
        menuService.insertOrUpdate(entity);
        return Response.ok("????????????");
    }

    @PostMapping("{id}/delete")
    @Log(logType = LogType.DELETE)
    @RequiresMethodPermissions("delete")
    public Response delete(@PathVariable("id") String id) {
        menuService.deleteById(id);
        return Response.ok("????????????");
    }

    @PostMapping("batch/delete")
    @Log(logType = LogType.DELETE)
    @RequiresMethodPermissions("delete")
    public Response batchDelete(@RequestParam("ids") String[] ids) {
        List<String> idList = java.util.Arrays.asList(ids);
        menuService.deleteBatchIds(idList);
        return Response.ok("????????????");
    }

    /**
     * ??????????????????
     *
     * @throws IOException
     */
    @GetMapping(value = "getMenus")
    public void getMenus(){
        List<Menu> treeNodeList = menuService.findMenuByUserId(UserUtils.getUser().getId());
        List<MenuTreeHelper.MenuTreeNode> menuTreeNodes = MenuTreeHelper.create().sort(treeNodeList);
        FastJsonUtils.print(menuTreeNodes);
    }

    @PostMapping("{id}/changeSort")
    public Response changeSort(@PathVariable("id") String id,
                                     @RequestParam ("sort") Integer sort) {
        menuService.changeSort(id, sort);
        return  Response.ok("????????????");
    }

    /**
     * ??????????????????
     *
     * @throws IOException
     */
    @GetMapping(value = "getPermissions")
    public void getPermissions(){
        //????????????
        String uid = UserUtils.getUser().getId();
        List<String> permissionValueList = menuService.findPermissionByUserId(uid);
        FastJsonUtils.print(permissionValueList);
    }

    @PostMapping("{id}/generate/button")
    @Log(logType = LogType.OTHER, title = "????????????")
    @RequiresMethodPermissions("generate:button")
    public Response generateButton(@PathVariable("id") String id,
                                   @RequestParam("parentPermission") String parentPermission,
                                   @RequestParam("permissions") String permissions,
                                   @RequestParam("permissionTitles") String permissionTitles) {
        menuService.generateButton(id, parentPermission, permissions.split(","), permissionTitles.split(","));
        return Response.ok("????????????");
    }
}