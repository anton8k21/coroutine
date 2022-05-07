import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.lang.Exception
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.*

val gson = Gson()
const val BASE_URL = "http://127.0.0.1:9999/"

fun main(args: Array<String>) {
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    with(CoroutineScope(EmptyCoroutineContext)){
        launch {
           try {
               val posts = getPost(client)

                   val postAndComments = posts.map { post ->
                   async {
                       PostWithComments(post = post.copy(author = getAuthor(post.authorId,client).name),getComment(client, post.id))
                   }
               }.awaitAll()
               println(postAndComments)
           }catch (e:Exception){
               e.printStackTrace()
           }
    }
    }
Thread.sleep(1000)




}

suspend fun getAuthor(id: Long, client: OkHttpClient): Author =
    makeRequest("${BASE_URL}api/authors/${id}", client, object : TypeToken <Author> () {})

suspend fun getComment(client: OkHttpClient, id: Long):List<Comment> =
    makeRequest("${BASE_URL}api/posts/${id}/comments", client, object : TypeToken<List<Comment>>() {})

suspend fun getPost(client: OkHttpClient): List<Post> =
    makeRequest("${BASE_URL}api/posts",client,object : TypeToken<List<Post>>() {})

suspend  fun <T> makeRequest(url: String, client: OkHttpClient, token: TypeToken<T>): T =
    withContext(Dispatchers.IO){
        client.apiCall(url).let { response ->
            if (!response.isSuccessful){
                response.close()
                throw RuntimeException(response.message)
            }
            val body = response.body?: throw RuntimeException("")
            gson.fromJson(body.charStream(), token.type)
        }
    }

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(::newCall)
            .enqueue(object : Callback{
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

            })
    }
}