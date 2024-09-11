
package azkaban.jobtype.util;

import bsp.encrypt.ParamType;

public class EncryptUtil {
	private static final String RRS_PEM = new StringBuilder()
			.append("MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC40IaZNIVDGX1L")
			.append("HsIq9YOJyXNBffp17fc8ZVADpoaFbVs33VXRuftLTcff/mq7uezR9leYpfIjqIJ6")
			.append("k4TLxyok/tVPp21QcE3yHi9AqH0IifDET+NwTAJU66Y0xY7TgjgmS54UX7ZmpEk3")
			.append("lNWDZkkQ1KWlVbHv4cqkE+iHTZLRrWWsJUJnFtcD25lRLinIJ9ptlie+dOFVmYrr")
			.append("zOxr/Ca8Q7z4vtOl4Eo0owX1IpsCngnHNAmDWWfGMvd46L/idT6IuTomdIg5uKJh")
			.append("htGY8bW5twbAZ7TAjhv/JCBAEhM6dcZyhR6TKfVCU8UYawlrzyadGhRG4sETaXhl")
			.append("Dmnr2tV/AgMBAAECggEBAI8w+WhQegPABwSh4zzXlj+2dndCvUCLzGfd8z2w0z8j")
			.append("uG4zLh/doib5L6iL/XRPnH4dCEd8I3yfPeDs1RHx0ORzESCzDw4oxSuXWXMWWDG6")
			.append("dnITl5tVOOVE4zS18HBNz2VUZzlP4wnptdS7myZApNHGgET2fXPnlFLGHf5fCycq")
			.append("iLUPzIwVd+SOwn1hgjhjD78nJcaEaU8tb/lcn4Gztpn3EgQY+4Y4VNah3fAMj6Th")
			.append("A+ZKZqEAYFqSG7CRwZcj+gpAU3JuaYOcEAOrYSn1gy0kl4Zd6AxK4IGLpqrzthlS")
			.append("oiUyqS32Soz66HdQtQJz2fzVVwzXa79xmQeQsT07/tkCgYEA6tGwiswgInNj8OwL")
			.append("kfQXao33HMvJsoZ7ZT1ANYyar6krLzmZQpSbnLTRintugVJbrW6J70wPlBT6Vhon")
			.append("beKDejZ/bpN1UIVlltRV1W0AGjRu9myweTeE8L2m1UWulcD4Pt6DrkCLroyGFsRa")
			.append("pDvL230F+PRRtr3rDJRLynAP1wsCgYEAyXwoyrlPL7tVUusNMDAJo+Mdm9jCOV0B")
			.append("mwrdRoxHLnTMvMeGppBk7xYZ8rsoIrIbLuOmtv6E9CFqc13e4POKAPNyJbR+kKJN")
			.append("eJBBMyjE6FOwHCuJYbWuFmV4jXq/SIMHz0gIr88idzwV5oBYENW4/MO1cfuKS/Tw")
			.append("fHUHXe1lM90CgYAuA0zexcT+OzI4QWi6/uOfw5XKlLw/OU7wtaHhXF3rUfDeXiEE")
			.append("BO7BNEVjJ3Ct8p94SpLIy1S6JaowOQvE4n/08LzjxA1W6+zOM2lmWueMOv4LV5z1")
			.append("A8YPDnqki/T770Y1u5B+ErPMTkjwKSXBzN3Tcpo7AFUKxAhM/LolPvQ7pQKBgFE2")
			.append("anrMFs72g33YoIg71KbqdJTM2fylMHB+AJLfGBHxolS1V+DrcsOr9OmR5quVfr93")
			.append("o0s/a/e7QF0gCSZDA+0+M1RfXGSQBwoBm0YzuKqskN/J7KYlxPXiEKV2RIPFzPAP")
			.append("6PB0XUASSAGQ2e5vNxErEYfQC/5xzD1eLon9lj1BAoGBALqR1odTRRr58JJdr+xB")
			.append("GtoBV650lf1alvzaXvnqKlOu9P9JSR6mSfV2RzcfDZ3BiOaDTYYIiBqbnjLDmUaR")
			.append("KmdLNbUoPCpw6JsxIO4Dl/m0Vyg4J0zA9LMKYls2GsEzchu5BLoD4ON8PGSSRIlw")
			.append("StD/mcdp3PExE8MG0JL3KOf2")
			.toString();

	private static final String SYS_PUB_PEM = new StringBuilder()
			.append("MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA0btzsPQAK6Ixddk6mNZ+")
			.append("ErDRtIe/9k8rhycp9YjhH6yWACmVIACk5EbxPKPRmVaVwMYjIw5e19t4TRAg9L0+")
			.append("YCAev7YSHReLUIV/7cYd35hXL7bpy8uT4zY2FYfNjjR1BcQlQUzTict+pq8OeeD8")
			.append("0u9X0bhrEis8OdLJKmytD7Ehn0BV1iIA9LCsyqFU4bL+xB98blagx7ruuZXMbFel")
			.append("aal4F19U1A4xTniH/IFA3rb9/whXQc7Gy9yM9zs/DtohQGCM+snj2lscIRhauUzw")
			.append("6GQB+3WFLcByjlvvUKUHRxOLFtTHTpjzmG+NMQpXMbkoM7Y0s71CIY9/mHjCtdaJ")
			.append("9+a1rgx7X2Rs6YEj5zJ3vxGwxhkfDDWxXSLTcNt/yQqLFDeQTIgM8fx/pdWLk1sI")
			.append("4uRnzTauNXVjkyXHIpYFyXUggGVVmjZosm+jtg5EsbmHIc5QnPX7IC3ZWsw68yAo")
			.append("4t5Hp2J3QKxa3OgpcRqjXsq0Dj/FPLWh1huWKS5UD3NtRO8zB8UJP+OIGG1yQMTk")
			.append("HLD0ihCsLXvJgVZyspMLSk/rDbnxUBPbARR1mN9vcfh8iEePnZKwiMIAshsTFKxE")
			.append("vFOfHH2J0zspbeilCK7re9FSYatnrQFkOodWXd9gcUb36yQ0u5MNNiQa7iwlgaG9")
			.append("X5oPHnk57d+biH8esTK4bP0CAwEAAQ==")
			.toString();
	
	
	public static String decryptPassword(final String encryptedPassword) throws Exception {
		String dec = bsp.encrypt.EncryptUtil.decrypt(ParamType.STRING,
				SYS_PUB_PEM,
				ParamType.STRING,
				RRS_PEM,
				ParamType.STRING,
				encryptedPassword);
		return dec;
	}

}
