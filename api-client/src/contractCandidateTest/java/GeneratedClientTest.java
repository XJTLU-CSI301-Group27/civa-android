import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.xjtlu.civa.api.AuthApi;
import edu.xjtlu.civa.api.model.CreateMealEntryRequest;
import edu.xjtlu.civa.api.model.CreateRecipeRequest;
import edu.xjtlu.civa.api.model.LoginRequest;
import edu.xjtlu.civa.api.model.User;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import retrofit2.Call;
import static org.junit.Assert.*;

public class GeneratedClientTest {
    @Test public void dtoAndRetrofitWireTypesMatchCandidate() throws Exception {
        Gson gson = new GsonBuilder().serializeNulls().create();
        String fixture = Files.readString(Path.of(System.getProperty("civa.fixtures"), "user-response.json"));
        User user = gson.fromJson(fixture.replaceFirst("\\{", "{\"futureField\":true,"), User.class);
        assertEquals("alice_01", user.getUsername());
        assertNull(user.getHouseholdId());
        assertTrue(gson.toJsonTree(new CreateRecipeRequest()).getAsJsonObject().get("imageId").isJsonNull());
        assertEquals(String.class, CreateMealEntryRequest.class.getMethod("getDate").getReturnType());
        assertEquals(Call.class, AuthApi.class.getMethod("login", LoginRequest.class).getReturnType());
    }
}
