# Tiyzi Müzik 2.1

Arka planda çalan, internetsiz dinlenebilen Android müzik uygulaması.

## Özellikler

**Keşfet**
- Audius'taki ücretsiz ve yasal şarkılarda arama
- Hızlı arama düğmeleri: Türkçe rap, Türkçe pop, Arabesk, Türkü...
- "Bu hafta popüler" listesi
- ⬇ ile indir, internetsiz dinle
- Ünlü sanatçılar için tek dokunuşla YouTube Music / Spotify'da açma

**Radyo**
- Yüzlerce Türk radyosu canlı (Power, Kral, TRT, Joy, Best FM...)
- Türlere göre filtre: Pop, Arabesk, Türkü, Rap, Slow, Fantezi, Sanat müziği...
- Radyo arama ve favori radyolar

**Kitaplığım**
- İndirilenler (internetsiz)
- Telefonum: telefondaki bütün MP3'leri otomatik bulur
- Favoriler ❤
- Çalma listeleri: oluştur, adlandır, sil, tümünü indir
- Listede arama ve sıralama (ada göre, sanatçıya göre, yeni eklenen)
- + ile dosyadan şarkı ekleme

**Görünüm**
- Liquid Glass: cam efektli paneller, yüzen alt çubuk ve hareketli renkli arka plan
- Keşfet sekmesindeki ⚙ Ayarlar'dan açılıp kapatılır

**Oynatıcı**
- Arka planda çalma, bildirim ve kilit ekranı kontrolleri
- Karışık çalma, tekrarlama (tümü / tek şarkı)
- Çalma sırası: görüntüleme, sıradan çıkarma, "Sıradaki olarak çal", "Sıraya ekle"
- Uyku zamanlayıcısı (5 dk – 1,5 saat ya da "şarkı bitince"), sesi yavaşça kısarak durdurur
- Ekolayzır + hazır ayarlar + bas güçlendirme (ayarlar kaydedilir)
- Çalma hızı (0.5x – 2x)
- Kulaklık çıkınca otomatik duraklatma

## Güncelleme nasıl yapılır?
1. Yeni zip'i çıkarın ve Android Studio'da **Open** ile `TiyziMuzik` klasörünü açın.
   (Eski klasörün üzerine yazabilir ya da yeni bir klasöre çıkarabilirsiniz.)
2. Alttaki **Terminal** sekmesine şunu yazın:
   ```
   .\gradlew assembleDebug
   ```
3. **BUILD SUCCESSFUL** yazınca APK burada olur:
   `app\build\outputs\apk\debug\app-debug.apk`
4. Dosyayı telefona atıp yükleyin. Eski uygulamanın üzerine güncellenir, indirdiğiniz şarkılar silinmez.

## En kolay yol: GitHub (Android Studio gerekmez)
Proje GitHub'a yüklenince APK'yı GitHub kendisi oluşturur. Telefondan şu bağlantıya
girip dosyaya dokunmanız yeterli:
`https://github.com/muhammedalibilgin47/TiyziM-zik/releases/latest/download/TiyziMuzik.apk`

Bu bağlantı her zaman en son sürümü indirir, arkadaşlarınıza da bunu gönderebilirsiniz.

## Not
Şarkılar Audius'tan (bağımsız sanatçıların ücretsiz paylaştığı müzikler), radyolar ise
Radio Browser'ın açık radyo listesinden gelir. Telifli şarkılar uygulamada indirilmez.
