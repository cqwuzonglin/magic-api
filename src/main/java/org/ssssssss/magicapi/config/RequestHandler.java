package org.ssssssss.magicapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.ssssssss.magicapi.model.JsonBean;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.MagicScriptEngine;
import org.ssssssss.script.exception.ScriptException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RequestHandler {

	private static Logger logger = LoggerFactory.getLogger(RequestHandler.class);

	private List<RequestInterceptor> requestInterceptors = new ArrayList<>();

	public void addRequestInterceptor(RequestInterceptor requestInterceptor) {
		requestInterceptors.add(requestInterceptor);
	}

	@ResponseBody
	public Object invoke(HttpServletRequest request, HttpServletResponse response,
						 @PathVariable(required = false) Map<String, Object> pathVariables,
						 @RequestParam(required = false) Map<String, Object> parameters,
						 @RequestBody(required = false) Map<String, Object> requestBody) {
		ApiInfo info;
		try {
			info = MappingHandlerMapping.getMappingApiInfo(request);
			MagicScriptContext context = new MagicScriptContext();
			putMapIntoContext(parameters, context);
			putMapIntoContext(pathVariables, context);
			if (requestBody != null) {
				context.set("body", requestBody);
			}
			// 执行前置拦截器
			for (RequestInterceptor requestInterceptor : requestInterceptors) {
				Object value = requestInterceptor.preHandle(info, context);
				if (value != null) {
					return value;
				}
			}
			Object value = MagicScriptEngine.execute(info.getScript(), context);
			// 执行后置拦截器
			for (RequestInterceptor requestInterceptor : requestInterceptors) {
				Object target = requestInterceptor.postHandle(info, context, value);
				if (target != null) {
					return target;
				}
			}
			return new JsonBean<>(value);
		} catch (Throwable root) {
			ScriptException se = null;
			Throwable parent = root;
			do {
				if (parent instanceof ScriptException) {
					se = (ScriptException) parent;
				}
			} while ((parent = parent.getCause()) != null);
			logger.error("执行接口出错", root);
			if (se != null) {
				return new JsonBean<>(-1, se.getSimpleMessage());
			}
			return new JsonBean<>(-1, root.getMessage());
		}

	}

	private void putMapIntoContext(Map<String, Object> map, MagicScriptContext context) {
		if (map != null && !map.isEmpty()) {
			Set<Map.Entry<String, Object>> entries = map.entrySet();
			for (Map.Entry<String, Object> entry : entries) {
				context.set(entry.getKey(), entry.getValue());
			}
		}
	}
}