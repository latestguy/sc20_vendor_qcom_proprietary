ifeq ($(strip $(TARGET_USES_QTIC_CMCC)),true)
include $(call all-subdir-makefiles)
endif
