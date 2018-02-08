package cn.com.process.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.IdentityService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngines;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricDetail;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricTaskInstanceQuery;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.apache.commons.lang.StringUtils;

/**
 * @author Administrator
 *
 */
public class ProcessManagerService {

	private ProcessEngine processEngine;
	private RuntimeService runtimeService;
	private FormService formService;
	private HistoryService historyService;
	private TaskService taskService;
	private IdentityService identityService;
	private RepositoryService repositoryService;

	public ProcessManagerService() {
		processEngine = ProcessEngines.getDefaultProcessEngine();
		runtimeService = processEngine.getRuntimeService();
		formService = processEngine.getFormService();
		taskService = processEngine.getTaskService();
		// 获取仓库服务
		repositoryService = processEngine.getRepositoryService();
		identityService = processEngine.getIdentityService();
		historyService = processEngine.getHistoryService();

	}

	/**
	 * 部署流程定义
	 * 
	 * @param fileNames
	 * @param processName
	 * @return
	 * @throws IOException
	 */
	public Deployment deployProcess(String[] fileNames, String processName) throws IOException {
		if (fileNames == null || fileNames.length == 0) {
			throw new IOException("传入的【" + fileNames + "】有误");
		}
		if (repositoryService == null) {
			throw new IOException("获取获取仓库服务失败");
		}
		// 创建发布配置对象
		DeploymentBuilder builder = repositoryService.createDeployment();
		if (StringUtils.isEmpty(processName)) {
			processName = "";
		}
		// 设置发布信息
		// 添加部署规则的显示别名;
		builder = builder.name(processName);
		// 添加规则文件和添加规则图片
		// 不添加会自动产生一个图片不推荐
		for (String fileName : fileNames) {
			builder = builder.addClasspathResource(fileName);
		}
		// 完成发布
		Deployment deployment = builder.deploy();
		return deployment;
	}

	/**
	 * 根据taskId获取HistoricDetail
	 * 
	 * @param taskId
	 * @return
	 * @throws IOException
	 */
	public List<HistoricDetail> findHistoricDetailByTask(String taskId) throws IOException {
		if (StringUtils.isEmpty(taskId)) {
			throw new IOException("传入的【" + taskId + "】有误");
		}
		return this.historyService.createHistoricDetailQuery().taskId(taskId).list();
	}

	/**
	 * 部署流程定义
	 * 
	 * @param zipFileName
	 * @param processName
	 * @return
	 * @throws IOException
	 */
	public Deployment deployProcessZip(String zipFileName, String processName) throws IOException {
		if (StringUtils.isEmpty(zipFileName)) {
			throw new IOException("传入的【" + zipFileName + "】有误");
		}
		// // 获取仓库服务
		// repositoryService = processEngine.getRepositoryService();
		// 创建发布配置对象
		DeploymentBuilder builder = repositoryService.createDeployment();
		// 获得上传文件的输入流程
		InputStream in = this.getClass().getClassLoader().getResourceAsStream(zipFileName);
		ZipInputStream zipInputStream = new ZipInputStream(in);
		// 设置发布信息
		if (StringUtils.isEmpty(processName)) {
			processName = "";
		}
		builder.name(processName)// 添加部署规则的显示别名
				.addZipInputStream(zipInputStream);
		// 完成发布
		Deployment deployment = builder.deploy();
		return deployment;
	}

	/**
	 * 启动一个流程实例
	 * 
	 * @param processInstanceVar
	 * @param processInstanceKey
	 * @param businessKey
	 *            业务主键（传入启动该流程的用户的ID）
	 * @return
	 * @throws IOException
	 */
	public ProcessInstance startProcessInstance(String processName, String businessKey,
			Map<String, Object> processInstanceVar) throws IOException {
		if (StringUtils.isEmpty(processName)) {
			throw new IOException("传入的【" + processName + "】有误");
		}
		if (processInstanceVar == null) {
			processInstanceVar = new HashMap<String, Object>();
		}
		// 根据processInstanceKey获取最新的流程创建流程实例
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processName, businessKey,
				processInstanceVar);
		return processInstance;
	}

	/**
	 * 删除一个流程定义
	 * 
	 * @param deploymentId
	 * @param cascade
	 * @throws IOException
	 */
	public void deleteDeployment(String deploymentId, boolean cascade) throws IOException {
		if (cascade) {
			// 级联删除,会删除和当前规则相关的所有信息，包括历史
			repositoryService.deleteDeployment(deploymentId, cascade);
		} else {
			// 普通删除，如果当前规则下有正在执行的流程，则抛异常
			repositoryService.deleteDeployment(deploymentId);
		}
	}

	/**
	 * 设置某个节点的审批人员
	 * 
	 * @param taskId
	 * @param user
	 */
	public void setApproveUser(String taskId, String assignee) throws IOException {
		if (StringUtils.isEmpty(taskId)) {
			throw new IOException("传入的【" + taskId + "】有误");
		}
		Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
		if (task == null) {
			throw new IOException("根据【" + taskId + "】获取不到Task有误");
		}
		if (StringUtils.isEmpty(assignee)) {
			throw new IOException("传入的【" + assignee + "】有误");
		}
		task.setAssignee(assignee);
		taskService.saveTask(task);
	}

	/**
	 * 根据任务ID获得任务实例
	 * 
	 * @param taskId
	 *            taskId
	 * @return
	 * @throws Exception
	 */
	public TaskEntity findTaskById(String taskId) throws IOException {
		if (StringUtils.isEmpty(taskId)) {
			throw new IOException("传入的【" + taskId + "】有误");
		}
		TaskEntity task = (TaskEntity) taskService.createTaskQuery().taskId(taskId).singleResult();
		if (task == null) {
			throw new IOException("任务实例未找到!");
		}
		return task;
	}

	/**
	 * @param taskId
	 * @param assignee
	 *            当前任务的办理人
	 * @param variables
	 * @throws IOException
	 */
	public void completeTask(String taskId, String assignee, Map<String, Object> variables) throws IOException {
		this.setApproveUser(taskId, assignee);
		if (variables == null) {
			variables = new HashMap<String, Object>();
		}
		if (StringUtils.isEmpty(taskId)) {
			throw new IOException("传入的【" + taskId + "】有误");
		}
		taskService.complete(taskId, variables);
	}

	/**
	 * @param taskId
	 *            当前任务ID
	 * @param variables
	 *            流程变量
	 * @throws Exception
	 */
	public void completeTask(String taskId, Map<String, Object> variables) throws IOException {
		if (variables == null) {
			variables = new HashMap<String, Object>();
		}
		if (StringUtils.isEmpty(taskId)) {
			throw new IOException("传入的【" + taskId + "】有误");
		}
		taskService.complete(taskId, variables);
	}

	/**
	 * 获取流程定义文件的资源
	 * 
	 * @param deploymentId
	 * @param type
	 *            type可以为png或者bpmn
	 * @return
	 * @throws IOException
	 */
	public ByteArrayOutputStream getProcessInstanceResouce(String deploymentId, String type) throws IOException {
		if (StringUtils.isEmpty(deploymentId)) {
			throw new IOException("传入的【" + deploymentId + "】有误");
		}
		if ("xml".equals(type) || "png".equals(type)) {

			// 从仓库中找需要展示的文件
			List<String> names = repositoryService.getDeploymentResourceNames(deploymentId);
			if (names == null || names.size() == 0) {
				throw new IOException("根据【" + deploymentId + "】获取不到图片");
			}
			String imageName = null;
			for (String name : names) {
				if (name.indexOf("." + type) >= 0) {
					imageName = name;
				}
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (imageName != null) {
				// 通过部署ID和文件名称得到文件的输入流
				InputStream in = repositoryService.getResourceAsStream(deploymentId, imageName);
				byte[] buffer = new byte[1024];
				int i = -1;
				while ((i = in.read(buffer)) != -1) {
					out.write(buffer, 0, i);
				}
			}
			return out;
		} else {
			throw new IOException("type只能是bpmn和png");
		}
	}

	/**
	 * 根据任务ID获取流程定义
	 * 
	 * @param taskId
	 *            任务ID
	 * @return
	 * @throws Exception
	 */
	public ProcessDefinitionEntity findProcessDefinitionEntityByTaskId(String taskId) throws IOException {
		// 取得流程定义
		ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) //
		((RepositoryServiceImpl) repositoryService)
				.getDeployedProcessDefinition(findTaskById(taskId).getProcessDefinitionId());
		if (processDefinition == null) {
			throw new IOException("根据【" + taskId + "】流程定义未找到!");
		}
		return processDefinition;
	}

	/**
	 * 根据任务ID和节点ID获取活动节点 <br>
	 * 
	 * @param taskId
	 *            任务ID
	 * @param activityId
	 *            活动节点ID <br>
	 *            如果为null或""，则默认查询当前活动节点 <br>
	 *            如果为"end"，则查询结束节点 <br>
	 * 
	 * @return
	 * @throws Exception
	 */
	private ActivityImpl findActivitiImpl(String taskId, String activityId) throws IOException {
		if (StringUtils.isEmpty(taskId)) {
			throw new IOException("传入的【" + taskId + "】有误");
		}
		// 取得流程定义
		ProcessDefinitionEntity processDefinition = findProcessDefinitionEntityByTaskId(taskId);
		// 获取当前活动节点ID
		if (StringUtils.isEmpty(activityId)) {
			activityId = findTaskById(taskId).getTaskDefinitionKey();
		}
		// 根据流程定义，获取该流程实例的结束节点
		if (activityId.toUpperCase().equals("END")) {
			for (ActivityImpl activityImpl : processDefinition.getActivities()) {
				List<PvmTransition> pvmTransitionList = activityImpl.getOutgoingTransitions();
				if (pvmTransitionList.isEmpty()) {
					return activityImpl;
				}
			}
		}
		// 根据节点ID，获取对应的活动节点
		ActivityImpl activityImpl = ((ProcessDefinitionImpl) processDefinition).findActivity(activityId);
		return activityImpl;
	}

	/**
	 * 获取某个节点的输出路径
	 * 
	 * @param taskId
	 * @param activityId
	 * @return
	 * @throws IOException
	 */
	public List<PvmTransition> findOutgoingTransitionsByTaskId(String taskId, String activityId) throws IOException {
		ActivityImpl activiti = findActivitiImpl(taskId, activityId);
		List<PvmTransition> activitis = new ArrayList<PvmTransition>();
		if (activiti != null) {
			activitis = activiti.getOutgoingTransitions();
		}
		return activitis;
	}

	/**
	 * 获取某个节点的输入路径
	 * 
	 * @param taskId
	 * @param activityId
	 * @return
	 * @throws IOException
	 */
	public List<PvmTransition> findIncomingTransitionsByTaskId(String taskId, String activityId) throws IOException {
		ActivityImpl activiti = findActivitiImpl(taskId, activityId);
		List<PvmTransition> activitis = new ArrayList<PvmTransition>();
		if (activiti != null) {
			activitis = activiti.getIncomingTransitions();
		}
		return activitis;
	}

	/**
	 * 获取当前流程实例的processInstanceId获取任务
	 * 
	 * @param processInstanceId
	 * @return
	 * @throws IOException
	 */
	public List<Task> findTasksByProcessInstanceId(String processInstanceId) throws IOException {
		if (StringUtils.isEmpty(processInstanceId)) {
			throw new IOException("传入的【" + processInstanceId + "】有误");
		}
		List<Task> tasks;
		tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
		if (tasks == null) {
			tasks = new ArrayList<Task>();
		}
		return tasks;
	}

	/**
	 * 查找正在运行的任务列表
	 * 
	 * @param assignee
	 * @param firstResult
	 * @param maxResults
	 * @param order
	 * @return
	 * @throws IOException
	 */
	public List<Task> findTasksByAssignee(String assignee, int firstResult, int maxResults, boolean order)
			throws IOException {
		if (StringUtils.isEmpty(assignee)) {
			throw new IOException("传入的【" + assignee + "】有误");
		}
		if (firstResult < 0) {
			firstResult = 0;
		}
		if (maxResults < 0) {
			maxResults = 0;
		}
		TaskQuery tq = taskService.createTaskQuery();
		tq = tq.taskAssignee(assignee);
		if (order) {
			tq = tq.orderByTaskCreateTime().desc();
		} else {
			tq = tq.orderByTaskCreateTime().asc();
		}
		List<Task> tasks = null;
		if (maxResults == 0) {
			tasks = tq.list();
		} else {
			tasks = tq.listPage(firstResult, maxResults);
		}
		return tasks;
	}

	/**
	 * 根据taskId获取历史Task记录
	 * 
	 * @param taskId
	 * @return
	 * @throws IOException
	 */
	public List<HistoricTaskInstance> findHistoricTaskInstanceByTaskId(String taskId) throws IOException {
		if (StringUtils.isEmpty(taskId)) {
			throw new IOException("传入的【" + taskId + "】有误");
		}
		HistoricTaskInstanceQuery hi = historyService.createHistoricTaskInstanceQuery();
		return hi.taskId(taskId).list();
	}

	/**
	 * 获取某个用户已完成或者没完成的任务
	 * 
	 * @param finished
	 * @return
	 */
	public List<HistoricTaskInstance> findHistoricTaskInstanceFinishedOrNot(String assignee, boolean finished)
			throws IOException {
		if (StringUtils.isEmpty(assignee)) {
			throw new IOException("传入的【" + assignee + "】有误");
		}
		HistoricTaskInstanceQuery hi = historyService.createHistoricTaskInstanceQuery().taskAssignee(assignee);
		if (finished) {
			return hi.finished().list();
		} else {
			return hi.unfinished().list();
		}
	}

	/**
	 * 获取某个用户已完成或者没完成的流程
	 * 
	 * @param finished
	 * @return
	 */
	public List<HistoricProcessInstance> findHistoricProcessInstanceFinishedOrNot(String businessKey, boolean finished)
			throws IOException {
		if (StringUtils.isEmpty(businessKey)) {
			throw new IOException("传入的【" + businessKey + "】有误");
		}
		HistoricProcessInstanceQuery hi = historyService.createHistoricProcessInstanceQuery()
				.processInstanceBusinessKey(businessKey);
		if (finished) {
			return hi.finished().list();
		} else {
			return hi.unfinished().list();
		}
	}

	/**
	 * 获取历史任务实例
	 * 
	 * @param assignee
	 * @param firstResult
	 * @param maxResults
	 * @param order
	 * @throws IOException
	 */
	public List<HistoricTaskInstance> findHistoricTaskInstance(String assignee, int firstResult, int maxResults,
			boolean order) throws IOException {
		if (StringUtils.isEmpty(assignee)) {
			throw new IOException("传入的【" + assignee + "】有误");
		}
		if (firstResult < 0) {
			firstResult = 0;
		}
		if (maxResults < 0) {
			maxResults = 0;
		}
		HistoricTaskInstanceQuery hi = historyService.createHistoricTaskInstanceQuery();
		hi = hi.taskAssignee(assignee);
		if (order) {
			hi = hi.orderByTaskCreateTime().desc();
		} else {
			hi = hi.orderByTaskCreateTime().asc();
		}
		List<HistoricTaskInstance> htiq = null;
		if (maxResults == 0) {
			htiq = hi.list();
		} else {
			htiq = hi.listPage(firstResult, maxResults);
		}
		return htiq;
	}

	/**
	 * 获取历史流程实例
	 * 
	 * @param processInstanceBusinessKey
	 *            创建流程实例时，传入的用户id
	 * @param firstResult
	 * @param maxResults
	 * @param order
	 * @return
	 * @throws IOException
	 */
	public List<HistoricProcessInstance> findHistoricProcessInstance(String processInstanceBusinessKey, int firstResult,
			int maxResults, boolean order) throws IOException {
		if (StringUtils.isEmpty(processInstanceBusinessKey)) {
			throw new IOException("传入的【" + processInstanceBusinessKey + "】有误");
		}
		if (firstResult < 0) {
			firstResult = 0;
		}
		if (maxResults < 0) {
			maxResults = 0;
		}
		HistoricProcessInstanceQuery hp = historyService.createHistoricProcessInstanceQuery();
		hp = hp.processInstanceBusinessKey(processInstanceBusinessKey);
		if (order) {
			hp = hp.orderByProcessInstanceEndTime().desc();
		} else {
			hp = hp.orderByProcessInstanceEndTime().asc();
		}
		List<HistoricProcessInstance> hpis = null;
		if (maxResults == 0) {
			hpis = hp.list();
		} else {
			hpis = hp.listPage(firstResult, maxResults);
		}
		return hpis;
	}

	/**
	 * 根据processInstanceId获取历史ProcessInstance记录
	 * 
	 * @param taskId
	 * @return
	 * @throws IOException
	 */
	public List<HistoricProcessInstance> findHistoricProcessInstanceByProcessInstanceId(String processInstanceId)
			throws IOException {
		if (StringUtils.isEmpty(processInstanceId)) {
			throw new IOException("传入的【" + processInstanceId + "】有误");
		}
		HistoricProcessInstanceQuery hpi = historyService.createHistoricProcessInstanceQuery();
		return hpi.processDefinitionId(processInstanceId).list();
	}

	/**
	 * 根据流程实例ID判断流程实例是否结束
	 * 
	 * @param processInstanceId
	 * @return
	 */
	public boolean isEnded(String processInstanceId) {
		Long count = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).count();
		if (count == 0) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 根据taskid获取运行时的任务的任务变量
	 * 
	 * @param taskId
	 * @return
	 * @throws IOException
	 */
	public Map<String, Object> findTaskVariablesByTaskId(String taskId) throws IOException {
		if (StringUtils.isEmpty(taskId)) {
			throw new IOException("传入的【" + taskId + "】有误");
		}
		TaskEntity task = this.findTaskById(taskId);
		if (task == null) {
			throw new IOException("根据【" + taskId + "】获取不到task");
		}
		String executionId = task.getExecutionId();
		return runtimeService.getVariables(executionId);
	}

	/**
	 * 根据processInstanceId查找历史变量
	 * 
	 * @param processInstanceId
	 * @return
	 * @throws IOException
	 */
	public List<HistoricVariableInstance> findHistoricVariableByprocessInstanceId(String processInstanceId)
			throws IOException {
		if (StringUtils.isEmpty(processInstanceId)) {
			throw new IOException("传入的【" + processInstanceId + "】有误");
		}
		return historyService.createHistoricVariableInstanceQuery().processInstanceId(processInstanceId).list();
	}

	/**
	 * 根据键子和key获取某个流程变量的值
	 * 
	 * @param taskId
	 * @param key
	 * @return
	 */
	public Object findTaskVariablesByTaskId(String taskId, String key) {
		try {
			Map<String, Object> vars = this.findTaskVariablesByTaskId(taskId);
			if (vars != null) {
				return vars.get(key);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}  
		return null;
	}

	public ProcessInstance findProcessInstance(String processInstanceId){
		return runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
	}
	
	
	public static void main(String[] args) throws IOException {
		ProcessManagerService pms = new ProcessManagerService();
		// Deployment dt = pms.deployProcess(new String[] { "diagram/svg.bpmn",
		// "diagram/svg.png" }, "svg");
		// Map<String, Object> var = new HashMap<String, Object>();
		// var.put("applyee", "gongdianju");
		// ProcessInstance pi = pms.startProcessInstance("svg", "gongdianju",
		// var);

		// String piid = "2500";
		// List<Task> tasks = pms.findTasksByProcessInstanceId(piid);
		// System.out.println(tasks.size()+ "\t"+tasks.get(0).getName());
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put("applye1e", "wangdiao");
		variables.put("check", "true");
		// Map<String, String> properties = new HashMap<String, String>();
		// properties.put("check", "true");
		// properties.put("useid", "zongjingli");
		// // pms.formService.submitTaskFormData("45004", properties);
		// pms.completeTask("45004", variables);
		pms.taskService.setVariables("70004", variables);
		pms.completeTask("70004", variables);
		System.out.println(pms.findHistoricProcessInstanceFinishedOrNot("gongdianju", false).size());
		System.out.println(pms.findHistoricProcessInstanceFinishedOrNot("gongdianju", true).size());
		System.out.println(pms.findHistoricTaskInstanceFinishedOrNot("gongdianju", true).size());
		System.out.println(pms.findHistoricTaskInstanceFinishedOrNot("gongdianju", false).size());
		System.out.println(pms.historyService.createHistoricProcessInstanceQuery().list().size());
	}

}
