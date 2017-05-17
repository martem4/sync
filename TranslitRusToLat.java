import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TranslitRusToLat {

    static final char[] rus = {'а', 'б' , 'в', 'г', 'д', 'е', 'ё', 'ж', 'з', 'и', 'й', 'к', 'л', 'м', 'н', 'о',
            'п', 'р', 'с',  'т', 'у' , 'ф', 'х', 'ц', 'ч', 'ш', 'щ', 'ы', 'э', 'ю', 'я'};

    static final String[] lat  = {"a", "b" , "v", "g", "d", "e", "jo", "zh", "z", "i", "j", "k", "l", "m", "n", "o",
            "p", "r", "s",  "t", "u" , "f", "h", "c", "ch", "sh", "w", "y", "e", "ju", "ja"};

    private static String translitChar(char c){
        for(int i=0; i < rus.length; i++){
            if(c==rus[i]){
                return lat[i];
            }
        }
        return null;
    }

    private static  void createRedmineAccount(List<JSONObject> redmineAccountList) throws IOException {
        final String url = "http://172.172.174.100/redmine/users.json?key=ac2c6958b64fe1cb59fb8e07ab662472810c695a";
        for (JSONObject j : redmineAccountList){
            JSONArray ja = new JSONArray();
            ja.add(j);
            JSONObject userJson = new JSONObject();
            userJson.put("user", ja);
            HttpPost postRequest = new HttpPost(url);
            postRequest.addHeader("content-type", "application/json; charset=UTF-8");
            String jsonString = userJson.toString().replace("[","").replace("]","");
            StringEntity params = new StringEntity(jsonString.toString(), "UTF-8");
            postRequest.setEntity(params);
            CloseableHttpClient httpClient = HttpClients.createDefault();
            try {
                httpClient.execute(postRequest);
                System.out.println("User : " + j.get("login") + " created.");
            } catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                httpClient.close();
            }
        }
    }

    public static String translitString(String word){;
        StringBuffer sb = new StringBuffer();
        if(word.length()>0){
            char[] chars = word.toCharArray();
            for(char c: chars){
                String res = translitChar(c);
                if (res != null){
                    sb.append(res);
                }
            }
        }
        return sb.toString();
    }

    private static List<JSONObject> getAccountFromFile(String fileName){
        List<JSONObject> redmineAccountList = new ArrayList();;
        try {
            FileReader fileReader = new FileReader(fileName);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;
            String login;
            String password="111111";
            String lastname;
            String firstname;
            String mail;

            while((line = bufferedReader.readLine()) != null){
                String[] words = line.split("\\s+");

                if (words.length>1){
                    lastname = words[0].replaceAll("[-+.^:,]","");
                    firstname = words[1].replaceAll("[-+.^:,]","");
                    login = translitChar(firstname.toLowerCase().charAt(0)) + translitString(lastname.toLowerCase());
                    if (words.length > 3 && words[3].contains("@"))
                        mail = words[3].trim();
                    else
                        mail = login+"@gk21.ru";

                    for (int i=2; i<words.length;i++){
                        if(words[i].matches(".*\\d+.*")){
                            String[] parts = words[i].split("\\D");
                            if(parts.length==3){
                                password = "";
                                for(String p : parts){
                                    if(p.length()==2){
                                        password += p;
                                    }
                                    else if(p.length() == 4){
                                        password += p.substring(2,4);
                                    }
                                }
                            }
                        }
                    }
                    JSONObject account = new JSONObject();
                    account.put("lastname", lastname);
                    account.put("firstname", firstname);
                    account.put("login", login);
                    account.put("password", password);
                    account.put("mail", mail);
                    password = "111111";
                    redmineAccountList.add(account);
                }

            }
            bufferedReader.close();
        }
        catch(FileNotFoundException ex) {
            System.out.println(
                    "Unable to open file '" +
                            fileName + "'");
        }
        catch(IOException ex) {
            System.out.println(
                    "Error reading file '"
                            + fileName + "'");
        }
        return redmineAccountList;
    }

    public static void main(String args[]){
        if (args.length > 0){
            String fileName = args[0];
            List<JSONObject> redmineAccountList = getAccountFromFile(fileName);
            try {
                createRedmineAccount(redmineAccountList);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
