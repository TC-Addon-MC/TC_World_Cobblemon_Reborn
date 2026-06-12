import java.lang.reflect.Method;
public class Inspect {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("com.cobblemon.mod.common.pokemon.Pokemon");
        for (Method m : clazz.getMethods()) {
            if (m.getName().toLowerCase().contains("stat") || m.getName().toLowerCase().contains("weight") || m.getName().toLowerCase().contains("form")) {
                System.out.println(m.getName() + " " + m.getReturnType().getName());
            }
        }
    }
}
