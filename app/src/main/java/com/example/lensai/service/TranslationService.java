package com.example.lensai.service;

import java.util.HashMap;
import java.util.Map;

public class TranslationService {
    private static final Map<String, String> dictionary = new HashMap<>();

    static {
        // Từ điển 101 lớp (Bạn có thể bổ sung thêm nếu muốn)
        dictionary.put("AlarmClock", "Đồng hồ báo thức");
        dictionary.put("Apple", "Quả táo");
        dictionary.put("ArmChair", "Ghế bành");
        dictionary.put("BaseballBat", "Gậy bóng chày");
        dictionary.put("BasketBall", "Quả bóng rổ");
        dictionary.put("Bathtub", "Bồn tắm");
        dictionary.put("Bed", "Giường");
        dictionary.put("Blinds", "Rèm cửa");
        dictionary.put("Book", "Quyển sách");
        dictionary.put("Boots", "Đôi ủng");
        dictionary.put("Bottle", "Cái chai");
        dictionary.put("Bowl", "Cái bát");
        dictionary.put("Box", "Cái hộp");
        dictionary.put("Bread", "Bánh mì");
        dictionary.put("ButterKnife", "Dao phết bơ");
        dictionary.put("Cabinet", "Tủ");
        dictionary.put("Candle", "Nến");
        dictionary.put("CellPhone", "Điện thoại");
        dictionary.put("Chair", "Ghế");
        dictionary.put("Cloth", "Khăn lau");
        dictionary.put("CoffeeMachine", "Máy pha cà phê");
        dictionary.put("CoffeeTable", "Bàn cà phê");
        dictionary.put("CounterTop", "Mặt bếp");
        dictionary.put("CreditCard", "Thẻ tín dụng");
        dictionary.put("Cup", "Cái cốc");
        dictionary.put("Curtains", "Màn cửa");
        dictionary.put("Desk", "Bàn làm việc");
        dictionary.put("DeskLamp", "Đèn bàn");
        dictionary.put("DiningTable", "Bàn ăn");
        dictionary.put("DishSponge", "Miếng rửa bát");
        dictionary.put("Drawer", "Ngăn kéo");
        dictionary.put("Dresser", "Tủ quần áo");
        dictionary.put("Egg", "Quả trứng");
        dictionary.put("Faucet", "Vòi nước");
        dictionary.put("FloorLamp", "Đèn sàn");
        dictionary.put("Footstool", "Ghế để chân");
        dictionary.put("Fork", "Cái nĩa");
        dictionary.put("Fridge", "Tủ lạnh");
        dictionary.put("GarbageCan", "Thùng rác");
        dictionary.put("HandTowel", "Khăn tay");
        dictionary.put("HandTowelHolder", "Giá treo khăn tay");
        dictionary.put("HousePlant", "Cây cảnh");
        dictionary.put("Kettle", "Ấm đun nước");
        dictionary.put("KeyChain", "Móc chìa khóa");
        dictionary.put("Knife", "Con dao");
        dictionary.put("Ladle", "Cái muôi");
        dictionary.put("Laptop", "Máy tính xách tay");
        dictionary.put("LaundryHamper", "Sọt đựng đồ");
        dictionary.put("Lettuce", "Rau diếp");
        dictionary.put("LightSwitch", "Công tắc đèn");
        dictionary.put("Microwave", "Lò vi sóng");
        dictionary.put("Mirror", "Gương");
        dictionary.put("Mug", "Cái ca");
        dictionary.put("Newspaper", "Báo giấy");
        dictionary.put("Ottoman", "Ghế đôn");
        dictionary.put("Painting", "Bức tranh");
        dictionary.put("Pan", "Cái chảo");
        dictionary.put("Pen", "Bút mực");
        dictionary.put("Pencil", "Bút chì");
        dictionary.put("PepperShaker", "Lọ hạt tiêu");
        dictionary.put("Pillow", "Gối");
        dictionary.put("Plate", "Cái đĩa");
        dictionary.put("Plunger", "Cái cao su thông cống");
        dictionary.put("Poster", "Áp phích");
        dictionary.put("Pot", "Cái nồi");
        dictionary.put("Potato", "Khoai tây");
        dictionary.put("RemoteControl", "Điều khiển từ xa");
        dictionary.put("Safe", "Két sắt");
        dictionary.put("SaltShaker", "Lọ muối");
        dictionary.put("ScrubBrush", "Bàn chải cọ");
        dictionary.put("Shelf", "Kệ");
        dictionary.put("ShowerCurtain", "Rèm phòng tắm");
        dictionary.put("ShowerDoor", "Cửa buồng tắm");
        dictionary.put("ShowerGlass", "Kính buồng tắm");
        dictionary.put("ShowerHead", "Vòi hoa sen");
        dictionary.put("SideTable", "Bàn nhỏ");
        dictionary.put("Sink", "Bồn rửa");
        dictionary.put("SoapBar", "Bánh xà phòng");
        dictionary.put("SoapBottle", "Chai xà phòng");
        dictionary.put("Sofa", "Ghế sofa");
        dictionary.put("Spatula", "Cái xẻng");
        dictionary.put("Spoon", "Cái thìa");
        dictionary.put("SprayBottle", "Bình xịt");
        dictionary.put("Statue", "Tượng");
        dictionary.put("StoveBurner", "Bếp ga");
        dictionary.put("TeddyBear", "Gấu bông");
        dictionary.put("Television", "Tivi");
        dictionary.put("TennisRacket", "Vợt tennis");
        dictionary.put("TissueBox", "Hộp giấy ăn");
        dictionary.put("Toaster", "Máy nướng bánh mì");
        dictionary.put("Toilet", "Bồn cầu");
        dictionary.put("ToiletPaper", "Giấy vệ sinh");
        dictionary.put("ToiletPaperHanger", "Giá treo giấy vệ sinh");
        dictionary.put("Tomato", "Quả cà chua");
        dictionary.put("Towel", "Khăn tắm");
        dictionary.put("TowelHolder", "Giá treo khăn");
        dictionary.put("Vase", "Lọ hoa");
        dictionary.put("Watch", "Đồng hồ đeo tay");
        dictionary.put("WateringCan", "Bình tưới cây");
        dictionary.put("Window", "Cửa sổ");
        dictionary.put("WineBottle", "Chai rượu");
    }

    // Hàm dịch: Nhận vào tiếng Anh, trả về tiếng Việt
    public static String toVietnamese(String englishLabel) {
        if (englishLabel == null) return "";
        String vietnamese = dictionary.get(englishLabel);
        return vietnamese != null ? vietnamese : englishLabel; // Nếu không có trong từ điển thì giữ nguyên tiếng Anh
    }
}