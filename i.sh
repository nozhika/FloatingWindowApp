git pull
git checkout -b tab-swiper        # cabang baru supaya aman

# 1. ekstrak zip
unzip -o tab-swiper.zip 

# 2. cari folder package yang benar
find app/src -name FloatingWindowService.java
# misal hasilnya: app/src/main/java/com/nozhika/floatingwindow/
JAVA_DIR=app/src/main/java/com/nozhika/floatingwindow    # sesuaikan

# 3. salin file (menimpa yang namanya sama, membuat folder bila belum ada)
mkdir -p "$JAVA_DIR" app/src/main/res/drawable
cp /tmp/ts/tab-swiper/java/*.java "$JAVA_DIR"/
cp /tmp/ts/tab-swiper/res/drawable/*.xml app/src/main/res/drawable/
cp /tmp/ts/tab-swiper/CHANGES.md ./TAB_SWIPER_CHANGES.md

# 4. periksa lalu commit dan push
git status
git add -A
git commit -m "Add tab swiper, app panel, and floating app windows"
git push -u origin tab-swiper
