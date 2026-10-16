package com.qualcomm.hardware.limelightvision;

import org.json.JSONObject;

/**
 * SIMULATOR ONLY: makes the SDK's own LLResult and LLStatus from a JSON result (the same JSON a real Limelight sends).
 * It sits in the SDK's package because LLResult.parse is not public.
 */
public final class SimLLResults {
    private SimLLResults() { }

    public static LLResult parse(JSONObject json) {
        return LLResult.parse(json);
    }

    public static LLStatus status(JSONObject json) {
        return new LLStatus(json);
    }
}
