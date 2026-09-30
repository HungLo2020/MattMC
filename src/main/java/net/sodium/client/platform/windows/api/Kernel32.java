package net.sodium.client.platform.windows.api;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.*;
import java.nio.ByteBuffer;

public class Kernel32 {
    private static final SharedLibrary LIBRARY = APIUtil.apiCreateLibrary("kernel32");

    private static final int MAX_PATH = 32767;

    private static final int GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT = 1;
    private static final int GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS = 1 << 2;

    private static final long PFN_GetCommandLineW;
    private static final long PFN_SetEnvironmentVariableW;

    private static final long PFN_GetModuleHandleExW;
    private static final long PFN_GetLastError;

    private static final long PFN_GetModuleFileNameW;


    static {
        PFN_GetCommandLineW = APIUtil.apiGetFunctionAddress(LIBRARY, "GetCommandLineW");
        PFN_SetEnvironmentVariableW = APIUtil.apiGetFunctionAddress(LIBRARY, "SetEnvironmentVariableW");
        PFN_GetModuleHandleExW = APIUtil.apiGetFunctionAddress(LIBRARY, "GetModuleHandleExW");
        PFN_GetLastError = APIUtil.apiGetFunctionAddress(LIBRARY, "GetLastError");
        PFN_GetModuleFileNameW = APIUtil.apiGetFunctionAddress(LIBRARY, "GetModuleFileNameW");
    }

    public static void setEnvironmentVariable(String name, @Nullable String value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer lpNameBuf = stack.malloc(16, MemoryUtil.memLengthUTF16(name, true));
            MemoryUtil.memUTF16(name, true, lpNameBuf);

            ByteBuffer lpValueBuf = null;

            if (value != null) {
                lpValueBuf = stack.malloc(16, MemoryUtil.memLengthUTF16(value, true));
                MemoryUtil.memUTF16(value, true, lpValueBuf);
            }

            JNI.callPPI(MemoryUtil.memAddress0(lpNameBuf), MemoryUtil.memAddressSafe(lpValueBuf), PFN_SetEnvironmentVariableW);
        }
    }

    public static long getCommandLine() {
        return JNI.callP(PFN_GetCommandLineW);
    }

    public static int getLastError() {
        return JNI.callI(PFN_GetLastError);
    }
}
