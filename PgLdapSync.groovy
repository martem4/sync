import java.security.MessageDigest
import groovy.sql.Sql
import javax.xml.bind.DatatypeConverter
import org.apache.commons.codec.binary.*
import com.novell.ldap.*
import java.lang.String

class PgLdapSync{
    	static void main(String[] args) {
    /*
        -подключиться к бд postgres
        -получить список пользователей
        -пройтись по списку пользователей из pg и проверять их наличие в ldap            
        -если пользователя нет, то создать его в Ldap
    */
        int ldapPort = 389
        int ldapVersion = 2
        String ldapHost = "172.172.173.18"
	    String ldapUser = "cn=Manager,dc=century,dc=local"
	    String ldapUserPass = "panasonic"
	    String pgUser = "postgres"
	    String pgPass = "oldaso"	   
	    String pgUrl = "jdbc:postgresql://172.172.173.173:5432/century"
        String base = "ou=people,dc=century,dc=local"


        def sql = Sql.newInstance(pgUrl, pgUser, pgPass, "org.postgresql.Driver")
//      def query = "select fam ,im , TO_CHAR(birth_date, 'DDMMYY') as birth_date ,p.id ,login from security_user su join people p on su.tabnum = p.id where su.is_active and p.is_active and login = 'emorozova';"
        def query = "select fam ,im , passw ,p.id ,lower(login) as login from tblusers777 su join people p on su.tabnum = p.id where p.is_active order by login;"

        LDAPConnection lc = new LDAPConnection()
        LDAPSearchResults searchResults = null
        lc.connect(ldapHost, ldapPort)
        lc.bind(ldapVersion, ldapUser ,ldapUserPass.getBytes("UTF-8"))

        sql.eachRow(query) { row ->

            String dn = "uid=" + row.login + ",ou=people,dc=century,dc=local"
            String filter = "uid="+row.login
            searchResults = lc.search(base, LDAPConnection.SCOPE_ONE, filter, null, false)
            sleep(3000)

            if(searchResults.getCount() == 0){
                        LDAPAttribute attribute = null;
                        LDAPAttributeSet attributeSet = new LDAPAttributeSet();
                        attributeSet.add(new LDAPAttribute("objectclass", new String("inetOrgPerson")));
                        attributeSet.add(new LDAPAttribute("sn", new String(row.fam)));
                        attributeSet.add(new LDAPAttribute("givenName", new String(row.im)));
                        attributeSet.add(new LDAPAttribute("displayName", new String(row.fam + " " + row.im)));
                        attributeSet.add(new LDAPAttribute("cn", new String(row.fam + " " + row.im)));
                        attributeSet.add(new LDAPAttribute("uid", new String(row.login)));
                        attributeSet.add(new LDAPAttribute("userPassword", binaryMd5Base64(row.passw)));
                        attributeSet.add(new LDAPAttribute("mail", new String(row.login + "@gk21.ru")));
                        LDAPEntry newEntry = new LDAPEntry(dn, attributeSet);
                        lc.add(newEntry);
                        println("\n-----------------------------------------------------------------")
                        println("User " + row.login + " created")
                        println("-----------------------------------------------------------------")
                    }
                    else if(searchResults.getCount() >= 1){
                        println("\n-----------------------------------------------------------------")
                        println("User " + row.login + " already exists")
                        println("-----------------------------------------------------------------")
            }
        }
            lc.disconnect();

    }

    private static String binaryMd5Base64(String passw) {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.reset();
        if (passw != null) {
            md.update(passw.getBytes("UTF-8"));
            return "{MD5}" + Base64.encodeBase64String(md.digest());
        }
        else {
            md.update("111111".getBytes("UTF-8"));
            return "{MD5}" + Base64.encodeBase64String(md.digest());
        }
    }
}
