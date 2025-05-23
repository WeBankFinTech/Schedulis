(function() {
    dust.register("flowsummary", body_0);
    function body_0(chk, ctx) {
        return chk.write("<div class=\"row\"><div class=\"col-xs-12\"><table class=\"table table-bordered table-condensed\"><tbody><tr><td class=\"property-key\">Project name</td><td>").reference(ctx.get(["projectName"], false), ctx, "h").write("</td></tr><tr><td class=\"property-key\">Job Types Used</td><td>").section(ctx.get(["jobTypes"], false), ctx, {
            "block": body_1
        }, null).write("</td></tr></tbody></table></div></div><div class=\"row\"><div class=\"col-xs-12\"><h3>Scheduling").exists(ctx.get(["schedule"], false), ctx, {
            "block": body_2
        }, null).write("</h3>").exists(ctx.get(["schedule"], false), ctx, {
            "else": body_3,
            "block": body_4
        }, null).write("<h3>Last Run Stats</h3></div></div>");
    }
    function body_1(chk, ctx) {
        return chk.reference(ctx.getPath(true, []), ctx, "h").write(" ");
    }
    function body_2(chk, ctx) {
        return chk.write("<div class=\"pull-right\"><button type=\"button\" id=\"removeSchedBtn\" class=\"btn btn-sm btn-danger\"onclick=\"removeSched(").reference(ctx.getPath(false, ["schedule", "scheduleId"]), ctx, "h").write(")\">" + wtssI18n.view.deleteSchedule + "</button></div>");
    }
    function body_3(chk, ctx) {
        return chk.write("<div class=\"callout callout-default\"><h4>None</h4><p>This flow has not been scheduled.</p></div>");
    }
    function body_4(chk, ctx) {
        return chk.write("<table class=\"table table-condensed table-bordered\"><tbody><tr><td class=\"property-key\">Schedule ID</td><td class=\"property-value-half\">").reference(ctx.getPath(false, ["schedule", "scheduleId"]), ctx, "h").write("</td><td class=\"property-key\">Submitted By</td><td class=\"property-value-half\">").reference(ctx.getPath(false, ["schedule", "submitUser"]), ctx, "h").write("</td></tr><tr><td class=\"property-key\">First Scheduled to Run</td><td class=\"property-value-half\">").reference(ctx.getPath(false, ["schedule", "firstSchedTime"]), ctx, "h").write("</td><td class=\"property-key\">").exists(ctx.getPath(false, ["schedule", "cronExpression"]), ctx, {
            "else": body_5,
            "block": body_6
        }, null).write("</td><td class=\"property-value-half\">").exists(ctx.getPath(false, ["schedule", "cronExpression"]), ctx, {
            "else": body_7,
            "block": body_8
        }, null).write("</td></tr><tr><td class=\"property-key\">Next Execution Time</td><td class=\"property-value-half\">").reference(ctx.getPath(false, ["schedule", "nextExecTime"]), ctx, "h").write("</td><td class=\"property-key\">SLA</td><td class=\"property-value-half\">").exists(ctx.getPath(false, ["schedule", "slaOptions"]), ctx, {
            "else": body_9,
            "block": body_10
        }, null).write("<div class=\"pull-right\"><button type=\"button\" id=\"addSlaBtn\" class=\"btn btn-xs btn-primary\"onclick=\"slaView.initFromSched(").reference(ctx.getPath(false, ["schedule", "scheduleId"]), ctx, "h").write(", '").reference(ctx.get(["projectName"], false), ctx, "h").write("', '").reference(ctx.get(["flowName"], false), ctx, "h").write("')\">" + wtssI18n.view.viewSetSLA + "</button></div></td></tr></tbody></table>");
    }
    function body_5(chk, ctx) {
        return chk.write("Repeats Every");
    }
    function body_6(chk, ctx) {
        return chk.write("Cron Expression");
    }
    function body_7(chk, ctx) {
        return chk.reference(ctx.getPath(false, ["schedule", "period"]), ctx, "h");
    }
    function body_8(chk, ctx) {
        return chk.reference(ctx.getPath(false, ["schedule", "cronExpression"]), ctx, "h");
    }
    function body_9(chk, ctx) {
        return chk.write("false");
    }
    function body_10(chk, ctx) {
        return chk.write("true");
    }
    return body_0;
}
)();
