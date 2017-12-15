import java.security.MessageDigest
import groovy.sql.Sql
import com.novell.ldap.LDAPConnection
import com.novell.ldap.LDAPModification
import com.novell.ldap.LDAPAttribute
import com.novell.ldap.LDAPException
import com.novell.ldap.LDAPEntry
import com.novell.ldap.LDAPSearchResults
import com.novell.ldap.LDAPAttributeSet
import org.apache.commons.codec.binary.*

import java.text.SimpleDateFormat

class PgLdapSync {

    static final int ldapPort = 389
    static int ldapVersion = 2
    static String ldapHost = "172.172.173.18"
    static String ldapUser = "cn=Manager,dc=century,dc=local"
    static String ldapUserPass = "panasonic"
    static String pgUser = "system"
    static String pgPass = "slafastat"
    static String pgDriver = "org.postgresql.Driver"
    static String pgUrl = "jdbc:postgresql://172.172.173.173:5432/century"
    static String base = "ou=people,dc=century,dc=local"
    static String syncLogFilePath = "sync.log"

    static final char[] rus = ['а', 'б', 'в', 'г', 'д', 'е', 'ё', 'ж', 'з', 'и', 'й',
                                'к', 'л', 'м', 'н', 'о', 'п', 'р', 'с', 'т', 'у', 'ф',
                                'х', 'ц', 'ч', 'ш', 'щ', 'ы', 'э', 'ю', 'я']

    static final String[] lat = ["a", "b", "v", "g", "d", "e", "jo", "zh", "z",
                                 "i", "j", "k", "l", "m", "n", "o", "p", "r", "s",
                                 "t", "u", "f", "h", "c", "ch", "sh", "w", "y", "e",
                                 "ju", "ja"]

    static String translitChar(char c) {
        for (int i = 0; i < rus.length; i++) {
            if (c == rus[i]) {
                return lat[i];
            }
        }
        return null
    }

    static String translitString(String word) {
        StringBuffer sb = new StringBuffer()
        if (word.length() > 0) {
            char[] chars = word.toCharArray()
            for (char c : chars) {
                String res = translitChar(c)
                if (res != null) {
                    sb.append(res)
                }
            }
        }
        return sb.toString()
    }

    static String binaryMd5Base64(String passw) {
        MessageDigest md = MessageDigest.getInstance("MD5")
        md.reset()
        if (passw != null) {
            md.update(passw.getBytes("UTF-8"))
            return "{MD5}${Base64.encodeBase64String(md.digest())}"
        }
        else {
            md.update("111111".getBytes("UTF-8"))
            return "{MD5}${Base64.encodeBase64String(md.digest())}"
        }
    }

    private static String getLastSyncTime() {
        File syncLogFile = new File(syncLogFilePath)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        String today = sdf.format(new Date())

        if (!syncLogFile.exists()){
            syncLogFile.createNewFile()
            return today
        }
        else {
            def lines = syncLogFile.readLines()
            if ((lines.size() > 0)) {
                if (lines.get(lines.size()-1) == null)
                    return today
                else return lines.get(lines.size()-1)
            }else return today
        }
    }

    private static void writeLastSyncTimeToLog(){
        File syncLogFile = new File(syncLogFilePath)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        String today = sdf.format(new Date())
        syncLogFile << '\n' + today
    }

    static void syncNewLdapUserFromPg(LDAPConnection lc, Sql sql) {

        def query = "select fam , im , TO_CHAR(birth_date, 'DDMMYY') as passw ,id\n" +
                " from people p\n" +
                " where p.is_active\n" +
                "  and p.date_create > '" + getLastSyncTime() + "'" +
                "  and  p.date_create < now()::date;\n"

        sql.eachRow(query) { row ->

            String fam = new String(row.fam)
            String im = new String(row.im)
            int loginIndex = 0
            String loginName = translitChar(im.toLowerCase().charAt(0))
            String loginSurname = translitString(fam.toLowerCase())
            String login = loginName + loginSurname

            def existFlag = true
            while(existFlag) {
                String dn = "uid=" + login + "," + base
                String filter = "uid=" + login
                LDAPSearchResults searchResults = lc.search(base, LDAPConnection.SCOPE_ONE, filter, null, false)
                sleep(3000)
                if (searchResults.getCount() == 0) {
                    existFlag = false
                    LDAPAttributeSet attributeSet = new LDAPAttributeSet()
                    attributeSet.add(new LDAPAttribute("objectclass", new String("inetOrgPerson")))
                    attributeSet.add(new LDAPAttribute("sn", fam))
                    attributeSet.add(new LDAPAttribute("givenName", im))
                    attributeSet.add(new LDAPAttribute("displayName", fam + " " + im))
                    attributeSet.add(new LDAPAttribute("cn", fam + " " + im))
                    attributeSet.add(new LDAPAttribute("uid", login))
                    attributeSet.add(new LDAPAttribute("userPassword", binaryMd5Base64(new String(row.passw))))
                    attributeSet.add(new LDAPAttribute("mail", new String(login + "@gk21.ru")))
                    attributeSet.add(new LDAPAttribute("employeeNumber", new String(row.id.toString())))
                    LDAPEntry newEntry = new LDAPEntry(dn, attributeSet)
                    lc.add(newEntry)
                    println("\n-----------------------------------------------------------------")
                    println("User " + login + " created")
                    println("-----------------------------------------------------------------")
                }
                else {
                    loginName += translitChar(im.toLowerCase().charAt(++loginIndex))
                    login = loginName + loginSurname
                }
            }
        }
        writeLastSyncTimeToLog()
    }

    static void syncQuitDate(LDAPConnection lc, Sql sql) {
        def query = "select fam , im , TO_CHAR(birth_date, 'DDMMYY') as passw ,id, quit_date\n"+
                    " from people\n"+
                    " where not quit_date is null\n"+
                     " order by quit_date;"

        LDAPSearchResults searchResults = null
        sql.eachRow(query) { row ->


            String filter = "employeeNumber=" + row.id.toString()
            searchResults = lc.search(base, LDAPConnection.SCOPE_ONE, filter, null, false)
            sleep(3000)

            if (searchResults.getCount() != 0) {
                while (searchResults.hasMore()) {
                    LDAPEntry entry = searchResults.next()
                    sleep(3000)
                    String entryDn = entry.getDN()

                    sleep(3000)
                    LDAPAttribute quitDateAttribute = new LDAPAttribute("quiteDate", new String(row.quit_date.toString()))
                    LDAPModification quitDateMod = new LDAPModification(LDAPModification.ADD, quitDateAttribute)

                    sleep(3000)
                    try {
                        lc.modify(entryDn, quitDateMod)
                        sleep(3000)
                        System.out.println(entryDn + " is successfully modified!")
                    }
                    catch (LDAPException e) {
                        if (e.getLDAPResultCode() == LDAPException.NO_SUCH_OBJECT) {
                            System.out.println("Error: No such entry")
                        } else if (e.getLDAPResultCode() == LDAPException.INSUFFICIENT_ACCESS_RIGHTS) {
                            System.out.println("Error: Insufficient rights")
                        } else if (e.getLDAPResultCode() == LDAPException.ATTRIBUTE_OR_VALUE_EXISTS) {
                            System.out.println("Error: Attribute or value exists")
                        } else
                            System.out.println("Error: " + e.toString())
                    }
                }
            }
        }
    }

    static void blockLdapUser(LDAPConnection lc, Sql sql) {
        def query = "select id\n"+
                    " from people p\n"+
                    " where p.is_active\n"+
                    " and p.quit_date >  '" + getLastSyncTime() + "'" +
                    " and p.quit_date < now()::date;\n"

        LDAPSearchResults searchResults = null
        sql.eachRow(query) { row ->

            String filter = "employeeNumber=" + row.id.toString()
            searchResults = lc.search(base, LDAPConnection.SCOPE_ONE, filter, null, false)
            sleep(3000)

            if (searchResults.getCount() != 0) {
                while (searchResults.hasMore()) {
                    LDAPEntry entry = searchResults.next()
                    String entryDn = entry.getDN()
                    def random = Math.abs(new Random().nextInt(1000000)) + 1
                    LDAPAttribute ldapAttribute = new LDAPAttribute("userPassword", random.toString())
                    sleep(3000)
                    LDAPModification ldapMod = new LDAPModification(LDAPModification.REPLACE, ldapAttribute)
                    sleep(3000)
                    try {
                        lc.modify(entryDn, ldapMod)
                        sleep(3000)
                        System.out.println(entryDn + " is successfully blocked!")
                    }
                    catch (LDAPException e) {
                        if (e.getLDAPResultCode() == LDAPException.NO_SUCH_OBJECT) {
                            System.out.println("Error: No such entry")
                        } else if (e.getLDAPResultCode() == LDAPException.INSUFFICIENT_ACCESS_RIGHTS) {
                            System.out.println("Error: Insufficient rights")
                        } else if (e.getLDAPResultCode() == LDAPException.ATTRIBUTE_OR_VALUE_EXISTS) {
                            System.out.println("Error: Attribute or value exists")
                        } else
                            System.out.println("Error: " + e.toString())
                    }
                }
            }
        }
    }


    static void main(String[] args) {
        /*
            -подключиться к бд postgres
            -получить список пользователей
            -пройтись по списку пользователей из pg и проверять их наличие в ldap
            -если пользователя нет, то создать его в Ldap
        */


        def sql = Sql.newInstance(pgUrl, pgUser, pgPass, pgDriver)


        LDAPConnection lc = new LDAPConnection()
        lc.connect(ldapHost, ldapPort)
        lc.bind(ldapVersion, ldapUser, ldapUserPass.getBytes("UTF-8"))

        syncNewLdapUserFromPg(lc, sql)
        blockLdapUser(lc, sql)

        lc.disconnect()

    }
}
