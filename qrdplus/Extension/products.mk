
PRODUCT_PACKAGES += \
    DeviceInfo \
    init.carrier.rc \
    SimContacts \
    qcril.db \
    libapkscanner \
    PhoneFeatures \
    com.qrd.wappush \
    com.qrd.wappush.xml \
    wrapper-updater \
    CarrierConfigure \
    CarrierLoadService \
    CarrierCacheService \
    NotificationService \
    TouchPal_Global \
    ArabicPack \
    BengaliPack \
    CangjiePack \
    ChtPack \
    HindiPack \
    IndonesianPack \
    MarathiPack \
    PortuguesebrPack \
    RussianPack \
    SpanishLatinPack \
    TagalogPack \
    TamilPack \
    TeluguPack \
    ThaiPack \
    VietnamPack \
    ProfileMgr \
    ExtSettings \
    SnapdragonSVA \
    TimerSwitch \
    QSService \
    PowerOffHandler \
    PowerOffHandlerApp \
    QTITaskManager \
    smartsearch \
    com.qualcomm.qti.smartsearch.xml \
    Firewall \
    LauncherUnreadService \
    NetworkControl \
    NotePad2 \
    PowerOnAlert \
    StorageCleaner \
    DataStorageCleanerService \
    ProfileMgr \
    CalendarWidget \
    LunarInfoProvider \
    ZeroBalanceHelper \
    libdatactrl \
    Setup_Wizard
ifneq ($(call is-board-platform-in-list, msm8998, msm8998_32),true)
  PRODUCT_PACKAGES += \
        ConfigurationClient \
        ConfigurationClientDemo \
        OmaDownload \
        libomadrmengine \
        libomadrmutils_jni \
        OmaDrmEngine \
        OmaDrmEngineDemo \
        OmaDrmEngineServer \
        ConfigurationClient
endif

PRODUCT_PACKAGE_OVERLAYS += vendor/qcom/proprietary/qrdplus/Extension/apps/BatterySaver/overlay

