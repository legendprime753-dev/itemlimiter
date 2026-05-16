import org.bukkit.potion.PotionType;
public class TestPotion {
    public static void main(String[] args) {
        for (PotionType type : PotionType.values()) {
            if (type.name().contains("HARMING")) {
                System.out.println(type.name());
            }
        }
    }
}
