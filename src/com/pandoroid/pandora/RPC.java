/* This file is part of Pandoroid
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.pandoroid.pandora;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.HttpResponseException;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.conn.scheme.Scheme;
//import cz.msebera.android.httpclient.conn.ssl.SSLSocketFactory;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpStatus;
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
//import okhttp3.tls.Certificates;
//import okhttp3.tls.HandshakeCertificates;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.MultipartBody;

/**
 * Description: This is the RPC client implementation for interfacing with 
 *  Pandora's servers. At the moment it uses Pandora's JSON API, but will
 *  hopefully be useful for whatever Pandora throws at us in the future.
 */
public class RPC {
    private HttpClient client;
    private final OkHttpClient client2;
    private String entity_type;
    private String request_url;
    private String user_agent;
    
    /**
     * Description: Our constructor class. This will set our default parameters
     *  for subsequent http requests, along with the MIME type for our entity
     *  (i.e. 'text/plain' for the current JSON protocol), and the partial URL 
     *  for the server (i.e. 'tuner.pandora.com/path/to/request/').
     */
    public RPC(String default_url, 
               String default_entity_type, 
               String default_user_agent){
        client = new DefaultHttpClient();
        client2 = new OkHttpClient();
        request_url = default_url;
        entity_type = default_entity_type;
        user_agent = default_user_agent;                
    }
    
    /**
     * Description: This function contacts the remote server with a string
     *  type data package (could be JSON), and returns the remote server's 
     *  response in a string.
     * @throws Exception if url_params or entity_data is empty/null.
     * @throws HttpResponseException if response is not equal HttpStatus.SC_OK
     * @throws IOException if a connection to the remote server can't be made.
     */
    public String call(Map<String, String> url_params, 
                       String entity_data,
                       boolean require_secure) throws Exception, 
                                               HttpResponseException,
                                               IOException{
        
        if (url_params == null || url_params.size() == 0){
            throw new Exception("Missing URL paramaters");
        }
        if (entity_data == null){
            throw new Exception("Missing data for HTTP entity.");
        }
        
        String full_url;
        
        if (require_secure){
            full_url = "https://" + request_url;
        }
        else{
            full_url = "http://" + request_url;
        }
        MediaType textPlainMT = MediaType.parse("text/plain; charset=utf-8");
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM) //this is what I say in my POSTman (Chrome plugin)
                .addFormDataPart("username", "android")
                .addFormDataPart("password", "AC7IBG09A3DTSYM4R41UJWL07VLN8JI7")
                .addFormDataPart("deviceModel", "D01")
                .addFormDataPart("version", "5")
                .build();
        
        //HttpPost request = new HttpPost();
        Request request = new Request.Builder()
                .url(full_url.concat(makeUrlParamString(url_params)))
                .post(requestBody)
                //.post(RequestBody.create(textPlainMT, helloMsg)).build()
                .addHeader("User-Agent", user_agent) //Notice this request has header if you don't need to send a header just erase this part
                .build();

        //Log.i("pandoroid", "URI: " + uri);
        Log.i("pandoroid", "URI: " + full_url.concat(makeUrlParamString(url_params)));
        StringEntity entity = null;
        
        try{
            entity = new StringEntity(entity_data);
            if (entity_type != null){
                entity.setContentType(entity_type);
                Log.i("pandoroid", "URI: " + entity_type);
            }
        }
        catch (Exception e){
            throw new Exception("Pandora RPC Http entity creation error");
        }
        
        //request.setEntity(entity);
        
        //Send to the server and get our response
        //client.getConnectionManager().getSchemeRegistry().register( new Scheme("https", SSLSocketFactory.getSocketFactory(), 443) );
        //HttpResponse response = client.execute(request);
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            //client2.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            //client2.hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Call call = client2.newCall(request);
        Response response = call.execute();
        int status_code = response.code();
        if (status_code != 200){
            throw new HttpResponseException(status_code, "HTTP status code: "
                                             + status_code + " != "
                                             + HttpStatus.SC_OK);
        }
        Log.i("pandoroid", "Responce: " + response.body().string());
        Log.i("pandoroid", "Responce: " + response.code());
        return response.body().string();
        //
        ////Read the response returned and turn it from a byte stream to a string.
        //HttpEntity response_entity = response.getEntity();
        //int BUFFER_BYTE_SIZE = 512;
        //String ret_data = new String();
        //byte[] bytes = new byte[BUFFER_BYTE_SIZE];
        //
        ////Check the entity type (usually 'text/plain'). Probably doesn't need
        ////to be checked.
//      //if (response_entity.getContentType().getValue().equals(entity_type)){
        //InputStream content = response_entity.getContent();
        //int bytes_read = BUFFER_BYTE_SIZE;
        //
        ////Rather than read an arbitrary amount of bytes, lets be sure to get
        ////it all.
        //while((bytes_read = content.read(bytes, 0, BUFFER_BYTE_SIZE)) != -1){
        //    ret_data += new String(bytes, 0, bytes_read);
        //}
//      //}
//      //else{
//      //    throw new Exception("Improper server response entity type: " +
//      //                        response_entity.getContentType().getValue());
//      //}
        //
        //return ret_data;
    }
    
    /**
     * Description: Here we create a URL method string with the parameters
     *  given. It automatically applies the '?' character to the beginning
     *  of strings, so multiple calls to this function will create an invalid 
     *  URL request string.
     */
    private String makeUrlParamString(Map<String, String> mapped_url_params){
        String url_string = "?";
        boolean first_loop = true;
        for (Map.Entry<String, String> entry : mapped_url_params.entrySet()){
            if (!first_loop){
                url_string += "&";
            }
            else{
                first_loop = false;
            }
            url_string += URLEncoder.encode(entry.getKey()) + "=" 
                          + URLEncoder.encode(entry.getValue());            
        }
        
        return url_string;
    }
}
