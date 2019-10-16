#ifndef __STREAMSOURCECLOCK_H__
#define __STREAMSOURCECLOCK_H__
/************************************************************************* */
/**
 * StreamSourceClock.h
 * @brief Header file for StreamSourceClock.
 *
 COPYRIGHT 2011-2013 Qualcomm Technologies, Inc.
 All rights reserved. Qualcomm Technologies proprietary and confidential.
 *
 ************************************************************************* */

/* =======================================================================
**               Include files for StreamSourceClock.h
** ======================================================================= */
#include "AEEStdDef.h"
#include "MMTimer.h"

/* -----------------------------------------------------------------------
** Constant / Macro Declarations
** ----------------------------------------------------------------------- */

/* -----------------------------------------------------------------------
** Type Declarations
** ----------------------------------------------------------------------- */

/* -----------------------------------------------------------------------
** Global Data Declarations
** ----------------------------------------------------------------------- */

/* -----------------------------------------------------------------------
** Forward Declarations
** ----------------------------------------------------------------------- */

/* =======================================================================
**                        Class & Function Declarations
** ======================================================================= */
class StreamSourceClock
{
public:
  StreamSourceClock(bool& result);
  ~StreamSourceClock();
  uint32 GetTickCount();
  uint32 GetElapsedTime(const uint32 startTime);
  double GetUTCTimeInMsec();
  // Callback registered with mmosal timer
  static void FetchCurrentUTCTime(void *arg);

private:
  //Member data
  uint32 m_nSystemTimeZero;
  double m_LastFetchedUTCTimeInMsec;
  double m_LastReturnedUTCTimeInMsec;
  uint32 m_nTickCountStartMsec;
  // Timer to query timedaemon current UTC time after every HTTP_UTC_TIMER_FETCH_INTERVAL
  MM_HANDLE mhTimer;
  MM_HANDLE m_pTimerLock;
};

#endif  /* __STREAMSOURCECLOCK_H__ */
