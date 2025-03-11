package azkaban.metric;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;

public enum ProjectHourlyReportAlertWay {
    RTX(1),
    EMAIL(2);

    int value;

    ProjectHourlyReportAlertWay(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    private static final ImmutableMap<Integer, ProjectHourlyReportAlertWay> NUM_VAL_MAP = Arrays.stream(ProjectHourlyReportAlertWay.values())
            .collect(ImmutableMap.toImmutableMap(alertWay -> alertWay.getValue(), alertWay -> alertWay));

    public static ProjectHourlyReportAlertWay fromInteger(final int x) {
        return NUM_VAL_MAP.getOrDefault(x, RTX);
    }

}
