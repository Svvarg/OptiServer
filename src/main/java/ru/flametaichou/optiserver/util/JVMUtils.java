package ru.flametaichou.optiserver.util;

import java.util.List;

/**
 *
 * @author Swarg
 */
public class JVMUtils
{
    public static int getOwnPid() {
        int pid = -1;
        try {
            java.lang.management.RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
            java.lang.reflect.Field jvm = runtime.getClass().getDeclaredField("jvm");
            jvm.setAccessible(true);
            sun.management.VMManagement mgmt = (sun.management.VMManagement) jvm.get(runtime);
            java.lang.reflect.Method pid_method = mgmt.getClass().getDeclaredMethod("getProcessId");
            pid_method.setAccessible(true);
            pid = (Integer) pid_method.invoke(mgmt);
        } catch(Exception e) {
        }
        return pid;
    }

    public static long getUpTime() {
        long uptime = -1L;
        try {
            java.lang.management.RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
            uptime = runtime.getUptime();
        }catch(Exception e) {
        }
        return uptime;
    }

    public static List<String> getArgsList() {
        List<String> list = null;
        try {
            java.lang.management.RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
            list = runtime.getInputArguments();
        }catch(Exception e) {
        }
        return list;
    }

    public static String getSystemProperty(String name) {
        try {
            java.lang.management.RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
            java.util.Map<String, String> map = runtime.getSystemProperties();
            if (map != null) return map.get(name);
        } catch(Exception e) {
        }
        return null;
    }
    
    public static String getRuntimeMXBeanClassName() {
        try {
            java.lang.management.RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
            if (runtime!=null) return runtime.getClass().getCanonicalName();
        } catch(Exception e) {
        }
        return "";
    }
}
