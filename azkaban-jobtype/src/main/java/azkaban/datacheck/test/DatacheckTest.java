package azkaban.datacheck.test;

import java.util.Base64;

public class DatacheckTest {
	
	public static void main(String[] args){
		
//		Properties p = new Properties();
//		p.setProperty("source.type", "bdp");
//		p.setProperty("data.object", "ad_audit_ods_mask.t_amis_plan");
//		
//		p.setProperty("source.type.01", "bdp");
//		p.setProperty("data.object.01", "ad_audit_ods_mask.t_amis_plan01");
//		
//		p.setProperty("source.type.02", "bdp");
//		p.setProperty("data.object.02", "ad_audit_ods_mask.t_amis_plan02");
//		
//		p.setProperty("source.type.1", "bdp");
//		p.setProperty("data.object.1", "ad_audit_ods_mask.t_amis_plan1");
//		
//		p.setProperty("source.type.2", "bdp");
//		p.setProperty("data.object.2", "ad_audit_ods_mask.t_amis_plan2");
		
		
//		p.setProperty("source.type", "bdp");
//		p.setProperty("data.object", "ad_audit_ods_mask.t_amis_plan");
		
//		p.setProperty("source.type.01", "bdp");
//		p.setProperty("data.object.01", "ad_audit_ods_mask.t_amis_plan");
		
//		p.setProperty("source.type.02", "job");
//		p.setProperty("data.object.2", "ad_report_ods_mask.rep_wepower_trans_sum");
//		p.setProperty("source.type", "job");
//		p.setProperty("data.object", "tpcds_text_10.store_sales");
//		p.setProperty("source.type.01", "job");
//		p.setProperty("data.object.01", "tpcds_text_10.store_sales");
//		p.setProperty("source.type.2", "job");
//		p.setProperty("data.object.2", "tpcds_text_10.store_sales");
//		p.setProperty("data.object.2", "cf_ds_ods_mask.ods_rcs_online_clause");
//		p.setProperty("data.object.3", "cf_ds_ods_mask.rcs_faq_tb_record{ds=20170911}");
//		p.setProperty("data.object.4", "cf_ds_ods_mask.ecif_product_info{ds=20170317}");
//		p.setProperty("data.object.5", "ccpd_dfqg1_safe.tbt_tm_acct_conf");
//		p.setProperty("data.object.6", "dbpd_aml_dm_safe.t07_case_stcr_ky_uh_hbase_20150608_bak");
//		p.setProperty("data.object.7", "hduser03ddb.report_jkc_cooperation_compensatory_detail_day");
//		p.setProperty("data.object.8", "cf_ds_ods_mask.ods_rcs_online_meanless_words");
//		p.setProperty("data.object.9", "cf_ds_ods_mask.ecif_contact_info{ds=20170913}");
//		p.setProperty("data.object.9", "ad_report_ods_mask.rep_wepower_trans_sum");
//		p.setProperty("data.object.10", "majunweidb3.mjw_pt2");
//		
//		p.setProperty("bdp.datachecker.jdo.option.name", "bdp");
//		p.setProperty("job.datachecker.jdo.option.url", "jdbc:mysql://10.107.108.111:3306/metastore");
//		p.setProperty("job.datachecker.jdo.option.username", "hive");
//		p.setProperty("job.datachecker.jdo.option.password", "aGl2ZSMyMDE4QDA3Cg==");
//
//		p.setProperty("job.datachecker.jdo.option.name", "job_");
//		p.setProperty("job.datachecker.jdo.option.url", "jdbc:mysql://10.107.108.111:3306/metastore");
//		p.setProperty("job.datachecker.jdo.option.username", "hive");
//		p.setProperty("job.datachecker.jdo.option.password", "aGl2ZSMyMDE4QDA3Cg==");
//
//		p.setProperty("wait.time", "1");
//		p.setProperty("query.frequency", "60");
		
//		DataChecker dc = new DataChecker("test", p);
//		
//		List<Properties> proList = dc.handleSeparationProperties(p);
//
//		for(Properties stp : proList){
//			System.out.println(stp.toString());
//		}
		
//		DataChecker dc2 = new DataChecker("test", p);
//		dc2.run();

		long s = 20140506L;
		System.out.println((s+"").length());
		try {
			final Base64.Decoder decoder = Base64.getDecoder();
			final Base64.Encoder encoder = Base64.getEncoder();
			final String text = "123456";
			final byte[] textByte = text.getBytes("UTF-8");
			//编码
			final String encodedText = encoder.encodeToString(textByte);
			System.out.println(encodedText);
			//解码
			System.out.println(new String(decoder.decode("aGl2ZSMyMDE4QDA3Cg=="), "UTF-8"));
		}catch(Exception e){

		}
	}
	

}
