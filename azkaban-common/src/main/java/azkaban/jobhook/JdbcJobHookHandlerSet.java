package azkaban.jobhook;

import azkaban.utils.GsonUtils;
import org.apache.commons.dbutils.ResultSetHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * @author lebronwang
 * @date 2022/08/08
 **/
public class JdbcJobHookHandlerSet {

  public static class JobHookResultHandler implements ResultSetHandler<List<JobHook>> {

    public static String SELECT_JOB_HOOKS =
        "SELECT job_code, prefix_rules, suffix_rules FROM hook_job_qualitis WHERE job_code = ? ";

    public static String LINK_JOB_HOOKS_SQL =
        "INSERT INTO hook_job_qualitis VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY "
            + "UPDATE prefix_rules=VALUES(prefix_rules), suffix_rules=VALUES(suffix_rules), "
            + "update_user=VALUES(update_user), update_time=VALUES(update_time) ";

    @Override
    public List<JobHook> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<JobHook> data = new ArrayList<>();
      do {
        final String jobCode = rs.getString(1);
        final String prefixRules = rs.getString(2);
        final String suffixRules = rs.getString(3);

        Map<String, Object> prefixRuleMap = GsonUtils.json2Map(prefixRules);
        Map<String, Object> suffixRuleMap = GsonUtils.json2Map(suffixRules);

        Map<Long, String> prefixRuleIds = new HashMap<>();
        Map<Long, String> suffixRuleIds = new HashMap<>();

        if (!prefixRules.isEmpty()) {
          Set<String> keySet = prefixRuleMap.keySet();
          for (String key : keySet) {
            Long ruleGroupId = Long.parseLong(key);
            String ruleGroupInfo = (String) prefixRuleMap.get(key);
            prefixRuleIds.put(ruleGroupId, ruleGroupInfo);
          }
        }

        if (!suffixRules.isEmpty()) {
          Set<String> keySet = suffixRuleMap.keySet();
          for (String key : keySet) {
            Long ruleGroupId = Long.parseLong(key);
            String ruleGroupInfo = (String) suffixRuleMap.get(key);
            suffixRuleIds.put(ruleGroupId, ruleGroupInfo);
          }
        }

        JobHook jobHook = new JobHook(jobCode, prefixRuleIds, suffixRuleIds);
        data.add(jobHook);
      } while (rs.next());

      return data;
    }
  }

}
