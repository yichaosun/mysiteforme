package com.mysiteforme.admin.controller.web;

import com.baomidou.mybatisplus.plugins.Page;
import com.google.common.collect.Maps;
import com.mysiteforme.admin.base.BaseController;
import com.mysiteforme.admin.entity.BlogArticle;
import com.mysiteforme.admin.entity.BlogChannel;
import com.mysiteforme.admin.entity.BlogComment;
import com.mysiteforme.admin.exception.MyException;
import com.mysiteforme.admin.lucene.LuceneSearch;
import com.mysiteforme.admin.util.RestResponse;
import com.mysiteforme.admin.util.ToolUtil;
import com.xiaoleilu.hutool.http.HTMLFilter;
import com.xiaoleilu.hutool.log.Log;
import com.xiaoleilu.hutool.log.LogFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

/**
 * Created by wangl on 2018/1/21.
 * todo:
 */
@RequestMapping("showBlog")
@Controller
public class BlogPageController extends BaseController{

    private static Log log = LogFactory.get();

    @Autowired
    private LuceneSearch luceneSearch;

    @PostMapping("click")
    @ResponseBody
    public RestResponse changeClicks(@RequestParam(value = "articleId",required = false) Long articleId){
        if(articleId == null){
            return RestResponse.failure("文章ID不能为空");
        }
        return RestResponse.success().setData(blogArticleService.flashArticleClick(articleId));
    }

    @GetMapping("test")
    @ResponseBody
    public RestResponse test(Long channelId){
        return RestResponse.success().setData(blogChannelService.getParentsChannel(channelId));
    }

    /**
     * 跳转首页
     * @param httpServletRequest
     * @param model
     * @return
     */
    @GetMapping(value = {"index","","/"})
    public String index(HttpServletRequest httpServletRequest,Model model){
        String href = httpServletRequest.getRequestURI();
        href = href.replaceFirst("/showBlog","");
        BlogChannel blogChannel = blogChannelService.getChannelByHref(href);
        model.addAttribute("channel",blogChannel);
        return "blog/index";
    }

    /**
     * 跳转文章专栏
     * @param httpServletRequest
     * @param model
     * @return
     */
    @GetMapping(value = {"/wzzl","/wzzl/**"})
    public String articleParttener(HttpServletRequest httpServletRequest,Model model) {
        String href = httpServletRequest.getRequestURI();
        href = href.replaceFirst("/showBlog","");
        if(href.endsWith("/")){
            href = href.substring(0,href.length()-1);
        }
        Map<String,Object> map = Maps.newHashMap();
        BlogChannel blogChannel = blogChannelService.getChannelByHref(href);
        if(blogChannel == null){
            throw new MyException("地址没找到",404);
        }
        if(blogChannel.getParentId() == null){
            map.put("rootId",blogChannel.getParentIds());
        }else {
            map.put("channelId",blogChannel.getId());
        }
        model.addAttribute("channel",blogChannel);
        Page<BlogArticle> pageList = blogArticleService.selectDetailArticle(map,new Page<BlogArticle>(1,10));
        model.addAttribute("pagelist",pageList);
        return "blog/article";
    }

    /**
     * 文章搜索
     * @param page
     * @param limit
     * @param key
     * @return
     * @throws Exception
     */
    @PostMapping("search")
    @ResponseBody
    public RestResponse searchArticle(@RequestParam(value = "page",defaultValue = "1")Integer page,
                                      @RequestParam(value = "limit",defaultValue = "10")Integer limit,
                                      @RequestParam(value = "keywords",required = false)String key) throws Exception {
        if(StringUtils.isBlank(key)){
            return RestResponse.failure("查询关键词不能为空");
        }
        String[] field = {"title","text"};
        Map<String,Object> data = luceneSearch.search(field,key,new Page<>(page,limit));
        return RestResponse.success().setData(data);
    }

    /**
     * 跳转文章详情
     * @param articleId
     * @param model
     * @return
     */
    @GetMapping("articleContent/{articleId}")
    public String articleContent(@PathVariable(value = "articleId",required = false)Long articleId,
                                 Model model){
        if(articleId == null || articleId <= 0){
            throw new MyException("文章ID不能为空");
        }
        BlogArticle article = blogArticleService.selectOneDetailById(articleId);
        if(article == null){
            throw new MyException("文章ID不存在");
        }
        model.addAttribute("article",article);
        return "blog/articleContent";
    }

    @PostMapping("saveComment")
    @ResponseBody
    public RestResponse add(BlogComment blogComment, HttpServletRequest request){
        if(StringUtils.isBlank(blogComment.getContent())){
            return RestResponse.failure("评论内容不能为空");
        }
        if(blogComment.getArticleId() == null) {
            return RestResponse.failure("评论文章ID不能为空");
        }
        if(blogComment.getChannelId() == null){
            return RestResponse.failure("文章所在栏目ID不能为空");
        }
        if(blogComment.getIp() != null){
            return RestResponse.failure("非法请求");
        }
        if(StringUtils.isNotBlank(blogComment.getIp())){
            return RestResponse.failure("非法请求");
        }
        if(blogComment.getFloor() != null){
            return RestResponse.failure("非法请求");
        }
        if(blogComment.getAdminReply() != null){
            return RestResponse.failure("非法请求");
        }
        if(blogComment.getDelFlag()){
            return RestResponse.failure("非法请求");
        }
        if(StringUtils.isNotBlank(blogComment.getRemarks())){
            return RestResponse.failure("非法请求");
        }
        HttpSession session = request.getSession();
        log.info("session的ID为"+session.getId());
        String content = new HTMLFilter().filter(blogComment.getContent());
        content.replace("\"", "'");
        if(content.length()>1000){
            return RestResponse.failure("您的评论内容太多啦！系统装不下啦！");
        }
        blogComment.setFloor(blogCommentService.getMaxFloor(blogComment.getArticleId())+1);
        Map<String,String> map = ToolUtil.getOsAndBrowserInfo(request);
        blogComment.setSystem(map.get("os"));
        blogComment.setBrowser(map.get("browser"));
        String ip = ToolUtil.getClientIp(request);
        if("0.0.0.0".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "localhost".equals(ip) || "127.0.0.1".equals(ip)){
            ip = "内网地址";
        }
        blogComment.setIp(ip);
        blogCommentService.insert(blogComment);
        return RestResponse.success();
    }

    /**
     * 获取文章评论
     * @param page
     * @param limit
     * @param articleId
     * @return
     */
    @PostMapping("articleCommentList")
    @ResponseBody
    public RestResponse articleCommentList(@RequestParam(value = "page",defaultValue = "1")Integer page,
                                           @RequestParam(value = "limit",defaultValue = "5")Integer limit,
                                           @RequestParam(value = "articleId",required = false)Long articleId){
        if(articleId == null){
            return RestResponse.failure("文章ID不能为空");
        }
        Page<BlogComment> pageData = blogCommentService.getArticleComments(articleId,new Page<BlogComment>(page,limit));
        return RestResponse.success().setData(pageData);
    }

}