package com.xiaoshu.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder.In;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageInfo;
import com.xiaoshu.config.util.ConfigUtil;
import com.xiaoshu.dao.DeptMapper;
import com.xiaoshu.entity.Attachment;
import com.xiaoshu.entity.Dept;
import com.xiaoshu.entity.Emp;
import com.xiaoshu.entity.EmpVo;
import com.xiaoshu.entity.Log;
import com.xiaoshu.entity.Operation;
import com.xiaoshu.entity.Role;
import com.xiaoshu.entity.User;
import com.xiaoshu.service.EmpService;
import com.xiaoshu.service.OperationService;
import com.xiaoshu.service.RoleService;
import com.xiaoshu.service.UserService;
import com.xiaoshu.util.StringUtil;
import com.xiaoshu.util.TimeUtil;
import com.xiaoshu.util.WriterUtil;

@Controller
@RequestMapping("emp")
public class EmpController extends LogController{
	static Logger logger = Logger.getLogger(EmpController.class);

	@Autowired
	private UserService userService;
	
	@Autowired
	private RoleService roleService ;
	
	@Autowired
	private OperationService operationService;
	
	
	@RequestMapping("empIndex")
	public String index(HttpServletRequest request,Integer menuid) throws Exception{
		List<Role> roleList = roleService.findRole(new Role());
		List<Operation> operationList = operationService.findOperationIdsByMenuid(menuid);
		//查询部门
		List<Dept> dlist = empService.findDept();
		request.setAttribute("operationList", operationList);
		request.setAttribute("roleList", roleList);
		request.setAttribute("dlist", dlist);
		return "emp";
	}
	
	
	@Autowired
	private EmpService empService;
	@RequestMapping(value="empList",method=RequestMethod.POST)
	public void userList(EmpVo emp,HttpServletRequest request,HttpServletResponse response,String offset,String limit) throws Exception{
		try {
			Integer pageSize = StringUtil.isEmpty(limit)?ConfigUtil.getPageSize():Integer.parseInt(limit);
			Integer pageNum =  (Integer.parseInt(offset)/pageSize)+1;
			PageInfo<EmpVo> userList= empService.findUserPage(emp,pageNum,pageSize);
			JSONObject jsonObj = new JSONObject();
			jsonObj.put("total",userList.getTotal() );
			jsonObj.put("rows", userList.getList());
	        WriterUtil.write(response,jsonObj.toString());
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("用户展示错误",e);
			throw e;
		}
	}
	
	
	// 新增或修改
	@RequestMapping("reserveEmp")
	public void reserveUser(MultipartFile picFile,HttpServletRequest request,Emp emp,HttpServletResponse response) throws Exception{
		Integer eid = emp.getEid();
		JSONObject result=new JSONObject();
		//上传图片
		if(picFile!=null && picFile.getSize()>0){
			//获取图片名称
			String filename = picFile.getOriginalFilename();
			//获取后缀
			String suffix = filename.substring(filename.lastIndexOf("."));
			//重新定义名称
			String newFileName = System.currentTimeMillis()+suffix;
			//设置虚拟路径
			File file = new File("d:/img/"+newFileName);
			//上传
			picFile.transferTo(file);
			//将新的文件名称保存到数据库
			emp.setPic(newFileName);
		}
		try {
			if (eid != null) {   // userId不为空 说明是修改
				Emp userName = empService.existUserWithUserName(emp.getEname());
				if(userName != null && userName.getEid().compareTo(eid)==0){
					emp.setEid(eid);
					empService.updateUser(emp);
					result.put("success", true);
				}else{
					result.put("success", true);
					result.put("errorMsg", "该用户名被使用");
				}
				
			}else {   // 添加
				if(empService.existUserWithUserName(emp.getEname())==null){  // 没有重复可以添加
					empService.addUser(emp);
					result.put("success", true);
				} else {
					result.put("success", true);
					result.put("errorMsg", "该用户名被使用");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("保存用户信息错误",e);
			result.put("success", true);
			result.put("errorMsg", "对不起，操作失败");
		}
		WriterUtil.write(response, result.toString());
	}
	
	
	@RequestMapping("deleteEmp")
	public void delUser(HttpServletRequest request,HttpServletResponse response){
		JSONObject result=new JSONObject();
		try {
			String[] ids=request.getParameter("ids").split(",");
			for (String id : ids) {
				empService.deleteUser(Integer.parseInt(id));
			}
			result.put("success", true);
			result.put("delNums", ids.length);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("删除用户信息错误",e);
			result.put("errorMsg", "对不起，删除失败");
		}
		WriterUtil.write(response, result.toString());
	}
	//导入方法
	@RequestMapping("importEmp")
	public void importEmp(MultipartFile importFile,HttpServletRequest request,HttpServletResponse response){
		JSONObject result=new JSONObject();
		try {
			//1.先获取导入的excel文件
			HSSFWorkbook wb = new HSSFWorkbook(importFile.getInputStream());
			//2.获取sheet页
			HSSFSheet sheet = wb.getSheetAt(0);
			//3.获取最后一行的行数
			int lastRowNum = sheet.getLastRowNum();
			//4.循环最后一行的行数
			for (int i = 1; i <= lastRowNum; i++) {
				//获取每一行的值(对象)
				HSSFRow row = sheet.getRow(i);
				//获取单元格内部的数据
				String ename = row.getCell(0).toString();
				Date birthday = row.getCell(1).getDateCellValue();
				Double numericCellValue = row.getCell(2).getNumericCellValue();
				int age = numericCellValue.intValue();
				String gender = row.getCell(3).toString();
				String img = row.getCell(4).toString();
				//导入员工部门时，需要根据部门名称查询部门id是否存在，如果导入的部门在表中不存在，那么需要将部门保存到部门表中
				//根据部门表名称查询部门id
				String depname = row.getCell(5).toString();
				Integer depid = findDeptIdByDepName(depname);
				//封装实体类
				Emp emp = new Emp();
				emp.setEname(ename);
				emp.setAge(age);
				emp.setBirthday(birthday);
				emp.setGender(gender);
				emp.setPic(img);
				emp.setDepid(depid);
				//保存方法
				empService.addUser(emp);
			}
			result.put("success", true);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("删除用户信息错误",e);
			result.put("errorMsg", "对不起，删除失败");
		}
		WriterUtil.write(response, result.toString());
	}
	@Autowired
	private DeptMapper deptMapper;
	//根据部门名称查询部门id
	public Integer findDeptIdByDepName(String dname){
		Dept dept = new Dept();
		dept.setDname(dname);
		Dept one = deptMapper.selectOne(dept);
		//如果部门名称在数据库中不存在，则直接将部门名称保存到数据库中
		if(one==null){
			deptMapper.insertDept(dept);
			one = dept;
		}
		return one.getDepid();
	}
	/*@RequestMapping("exportEmp")
	public void backup(HttpServletRequest request,HttpServletResponse response){
		JSONObject result = new JSONObject();
		try {
			String time = TimeUtil.formatTime(new Date(), "yyyyMMddHHmmss");
		    String excelName = "手动备份"+time;
			EmpVo emp = new EmpVo();
			List<EmpVo> list = empService.findPage(emp);
			String[] handers = {"序号","操作人","IP地址","操作时间","操作模块","操作类型","详情"};
			// 1导入硬盘
			ExportExcelToDisk(request,handers,list, excelName);
			result.put("success", true);
		} catch (Exception e) {
			e.printStackTrace();
			result.put("", "对不起，备份失败");
		}
		WriterUtil.write(response, result.toString());
	}
	
	// 导出到硬盘
	@SuppressWarnings("resource")
	private void ExportExcelToDisk(HttpServletRequest request,
			String[] handers, List<EmpVo> list, String excleName) throws Exception {
		
		try {
			HSSFWorkbook wb = new HSSFWorkbook();//创建工作簿
			HSSFSheet sheet = wb.createSheet("操作记录备份");//第一个sheet
			HSSFRow rowFirst = sheet.createRow(0);//第一个sheet第一行为标题
			rowFirst.setHeight((short) 500);
			for (int i = 0; i < handers.length; i++) {
				sheet.setColumnWidth((short) i, (short) 4000);// 设置列宽
			}
			//写标题了
			for (int i = 0; i < handers.length; i++) {
			    //获取第一行的每一个单元格
			    HSSFCell cell = rowFirst.createCell(i);
			    //往单元格里面写入值
			    cell.setCellValue(handers[i]);
			}
			for (int i = 0;i < list.size(); i++) {
			    //获取list里面存在是数据集对象
			    EmpVo empVo = list.get(i);
			    //创建数据行
			    HSSFRow row = sheet.createRow(i+1);
			    //设置对应单元格的值
			    row.setHeight((short)400);   // 设置每行的高度
			    //"序号","操作人","IP地址","操作时间","操作模块","操作类型","详情"
			    row.createCell(0).setCellValue(i+1);
			    row.createCell(1).setCellValue(empVo.getEname());
			}
			//写出文件（path为文件路径含文件名）
				OutputStream os;
				File file = new File(request.getSession().getServletContext().getRealPath("/")+"logs"+File.separator+"backup"+File.separator+excleName+".xls");
				
				if (!file.exists()){//若此目录不存在，则创建之  
					file.createNewFile();  
					logger.debug("创建文件夹路径为："+ file.getPath());  
	            } 
				os = new FileOutputStream(file);
				wb.write(os);
				os.close();
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
	}*/
	
	//导出excel
	@RequestMapping("exportEmp")
	public void exportEmp(HttpServletRequest request,HttpServletResponse response){
		JSONObject result=new JSONObject();
		try {
			//1.创建excel文件 HSSFWebBook
			HSSFWorkbook wb = new HSSFWorkbook();
			//2.创建sheet页
			HSSFSheet sheet = wb.createSheet();
			//3.创建行
			HSSFRow createRow = sheet.createRow(0);
			//定义表头信息
			String[] header = {"员工编号","员工姓名","员工生日","员工年龄","员工性别","员工头像","员工部门"};
			//将表头信息放入第一行内部，并且创建单元格放入
			for (int i = 0; i < header.length; i++) {
				//获取单元格
				HSSFCell cell = createRow.createCell(i);
				//将表头信息放入单元格
				cell.setCellValue(header[i]);
			}
			//4.从数据库将数据查询出来
			List<EmpVo> list = empService.findPage(new EmpVo());
			//5.循环数据，将数据依次保存到单元格内
			for (int i = 0; i < list.size(); i++) {
				//获取商品信息
				EmpVo empVo = list.get(i);
				//获取到对象之后判断性别就可以了
				//导出性别为男，并且部门是财务部的人
				if(empVo.getGender().equals("女")){
					list.remove(empVo);
					i++;
					continue;
				}
				if(empVo.getDepid()!=6){
					list.remove(empVo);
					i++;
					continue;
				}
				//将获取到的对象属性依次保存到excel的单元格内
				HSSFRow row = sheet.createRow(i+1);
				//获取单元格保存数据
				row.createCell(0).setCellValue(empVo.getEid());
				row.createCell(1).setCellValue(empVo.getEname());
				row.createCell(2).setCellValue(TimeUtil.formatTime(empVo.getBirthday(), "yyyy-MM-dd"));
				row.createCell(3).setCellValue(empVo.getAge());
				row.createCell(4).setCellValue(empVo.getGender());
				row.createCell(5).setCellValue(empVo.getPic());
				//根据部门id查询部门名称
				//String dname = empService.findDname(empVo.getDepid());
				row.createCell(6).setCellValue(empVo.getDname());
			}
			/*//将excel导入本地
			File file = new File("D:/img/员工信息1.xls");
			if(!file.exists()){
				file.createNewFile();
				logger.debug("创建文件的路径为："+file.getPath());
			}
			//导出，使用output流
			OutputStream out = new FileOutputStream(file);
			wb.write(out);
			out.close();
			wb.close();*/
			response.setHeader("Content-Disposition", "attachment;filename="+URLEncoder.encode("员工列表.xls", "UTF-8"));
			response.setHeader("Connection", "close");
			response.setHeader("Content-Type", "application/octet-stream");
	        wb.write(response.getOutputStream());
			wb.close();
			result.put("success", true);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("删除用户信息错误",e);
			result.put("errorMsg", "对不起，删除失败");
		}
		//WriterUtil.write(response, result.toString());
	}
	
	@RequestMapping("editPassword")
	public void editPassword(HttpServletRequest request,HttpServletResponse response){
		JSONObject result=new JSONObject();
		String oldpassword = request.getParameter("oldpassword");
		String newpassword = request.getParameter("newpassword");
		HttpSession session = request.getSession();
		User currentUser = (User) session.getAttribute("currentUser");
		if(currentUser.getPassword().equals(oldpassword)){
			User user = new User();
			user.setUserid(currentUser.getUserid());
			user.setPassword(newpassword);
			try {
				userService.updateUser(user);
				currentUser.setPassword(newpassword);
				session.removeAttribute("currentUser"); 
				session.setAttribute("currentUser", currentUser);
				result.put("success", true);
			} catch (Exception e) {
				e.printStackTrace();
				logger.error("修改密码错误",e);
				result.put("errorMsg", "对不起，修改密码失败");
			}
		}else{
			logger.error(currentUser.getUsername()+"修改密码时原密码输入错误！");
			result.put("errorMsg", "对不起，原密码输入错误！");
		}
		WriterUtil.write(response, result.toString());
	}
}
