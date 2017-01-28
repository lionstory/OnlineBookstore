package com.chicken.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import com.chicken.model.ShopBean;
import com.chicken.model.ShopBeanCl;
import com.chicken.model.UserBean;
import com.chicken.model.UserBeanCl;
import com.chicken.util.C;

@WebServlet("/ShopInfoClServlet")
public class ShopInfoClServlet extends HttpServlet {
	
	/**
	 * 处理与"店铺信息"相关请求
	 * 1.从数据库中读取店铺信息
	 * 2.写入数据库店铺信息
	 */
	private static final long serialVersionUID = 1L;

	public ShopInfoClServlet() {
        super();
    }

	@SuppressWarnings("unchecked")
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		
		//先验证用户是否登录
		HttpSession session=request.getSession(true);
		String username=(String) session.getAttribute("username");
		String dowhat = request.getParameter("dowhat");//获取操作类型
		
		if(username == null) //未登录，跳转至登录界面
		{
			request.getRequestDispatcher("login_page.jsp").forward(request, response);
			return;
		}
		else
		{
			ShopBeanCl shopBeanCl = new ShopBeanCl();
			if(dowhat.equals("openShop")) //若用户请求开店
			{
				//进行开店处理
				shopBeanCl.addShop(username);
				request.getRequestDispatcher("ShopInfoClServlet?dowhat=queryShopInfo").forward(request, response);
			}
			else //其它需要操作店铺的请求
			{
				//率先验证该用户有没有开店
				int sid = shopBeanCl.getSidByUid(username);
				request.setAttribute("Sid", sid); //方便jsp页面识别用户是否开店 进而显示不同内容
				
				if(sid == C.OPENED_SHOP_NO) //没开店则直接返回仅包含一个“我要开店”按钮的界面
				{
					request.getRequestDispatcher("shopinfo_page.jsp").forward(request, response);
					return;
				}
				
				//开了店的
				if(dowhat.equals("updateShopInfo"))
				{				
					
					//得到上传文件的保存目录，将上传的文件存放于WEB-INF目录下，不允许外界直接访问，保证上传文件的安全
					String savePath = this.getServletContext().getRealPath("/images/shopicons");
					File file = new File(savePath);
					
					//判断上传文件的保存目录是否存在
					if (!file.exists() && !file.isDirectory()) 
					{
						System.out.println(savePath+"目录不存在，需要创建");
						//创建目录
						file.mkdir();
					}
					
					//消息提示
					String message = "";
					String sicon="";
					ShopBean updatedShop=null;
					try
					{
						UserBeanCl userBeanCl = new UserBeanCl();
						
						//使用Apache文件上传组件处理文件上传步骤：
						//1、创建一个DiskFileItemFactory工厂
						DiskFileItemFactory factory = new DiskFileItemFactory();
						//2、创建一个文件上传解析器
						ServletFileUpload upload = new ServletFileUpload(factory);
						//解决上传文件名的中文乱码
						upload.setHeaderEncoding("UTF-8"); 
						//3、判断提交上来的数据是否是上传表单的数据
						if(!ServletFileUpload.isMultipartContent(request))
						{
							//按照传统方式获取数据
							return;
						}
						//4、使用ServletFileUpload解析器解析上传数据，解析结果返回的是一个List<FileItem>集合，每一个FileItem对应一个Form表单的输入项
						List<FileItem> list = upload.parseRequest(request);
						for(FileItem item : list)
						{
							//如果fileitem中封装的是普通数据
							if(item.isFormField())
							{
								String name = item.getFieldName();
								String value = item.getString("utf-8");
								request.setAttribute(name, value);
							}
							else //文件数据
							{						
								//如果fileitem中封装的是上传文件
								//生成头像图片的名称（username.jpg）
								sicon = sid + ".jpg";
								
								//若没有上传图片，则用以前那个，不再写入到服务器文件夹下了
								String oriFileName = item.getString();					
								if(oriFileName==null || oriFileName.trim().equals(""))
								{
									//获取数据库中图标的名称
									sicon = shopBeanCl.getSiconBySid(sid);

									continue;
								}
								
								//注意：不同的浏览器提交的文件名是不一样的，有些浏览器提交上来的文件名是带有路径的，如： c:\a\b\1.txt，而有些只是单纯的文件名，如：1.txt
								//处理获取到的上传文件的文件名的路径部分，只保留文件名部分
								sicon = sicon.substring(sicon.lastIndexOf("\\")+1);
								//获取item中的上传文件的输入流
								InputStream in = item.getInputStream();
								//创建一个文件输出流
								FileOutputStream out = new FileOutputStream(savePath + "\\" + sicon);
								//创建一个缓冲区
								byte buffer[] = new byte[1024];
								//判断输入流中的数据是否已经读完的标识
								int len = 0;
								//循环将输入流读入到缓冲区当中，(len=in.read(buffer))>0就表示in里面还有数据
								while((len=in.read(buffer))>0)
								{
									//使用FileOutputStream输出流将缓冲区的数据写入到指定的目录(savePath + "\\" + filename)当中
									out.write(buffer, 0, len);
								}

								//关闭输入流
								in.close();
								//关闭输出流
								out.close();
								//删除处理文件上传时生成的临时文件
								item.delete();
								message = "文件上传成功！";
							}
						}
						
						//获取非文件数据
						String sname = (String)request.getAttribute("shopname");
						float stransprice = Float.parseFloat((String)request.getAttribute("transprice"));
						String ssummary = (String) request.getAttribute("summary");
						String sactivity = (String)request.getAttribute("activity");
						
						//更新数据库
						updatedShop = new ShopBean(sid, sname, username, sicon, ssummary, sactivity, stransprice);
						shopBeanCl.updateShop(updatedShop,sicon);
		
					}catch (Exception e) {
						
						message= "文件上传失败！";
						e.printStackTrace();			 
					}
					
					//转发
					request.setAttribute("shopBean", updatedShop);
					request.getRequestDispatcher("shopinfo_page.jsp").forward(request, response);
					return;
				}
				else if(dowhat.equals("queryShopInfo"))//点击“店铺信息”选项卡
				{
					//查询username用户信息
					ShopBean shopBean = shopBeanCl.findShopBySid(sid);
		
					//转发
					request.setAttribute("shopBean", shopBean);
					request.getRequestDispatcher("shopinfo_page.jsp").forward(request, response);
					return;
				}
			}
		}
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}
}
