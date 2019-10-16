
/************************************************************************* */
/**
 * @file StreamSourceClock.cpp
 * @brief Implementation of StreamSourceClock.
 *  StreamSourceClock is a wrapper class that acts as a reference clock for
 *  the Stream Source module. Internally it makes use of the ISysTimer for system time.
 *
 COPYRIGHT 2011-2013 Qualcomm Technologies, Inc.
 All rights reserved. Qualcomm Technologies proprietary and confidential.
 *
 ************************************************************************* */

/* =======================================================================
**               Include files for StreamSourceClock.cpp
** ======================================================================= */
#include "StreamSourceClock.h"
#include "qtv_msg.h"
#include "StreamSourceTimeUtils.h"
#include <MMCriticalSection.h>

/* =======================================================================
**                      Data Declarations
** ======================================================================= */

/* -----------------------------------------------------------------------
** Constant / Macro Definitions
** ----------------------------------------------------------------------- */
/* Frequency at which UTC time is queried from time_daemon */
#define HTTP_UTC_TIMER_FETCH_INTERVAL 1000

/* -----------------------------------------------------------------------
** Type Declarations
** ----------------------------------------------------------------------- */

/* -----------------------------------------------------------------------
** Global Constant Data Declarations
** ----------------------------------------------------------------------- */

/* -----------------------------------------------------------------------
** Global Data Declarations
** ----------------------------------------------------------------------- */
//uint32 RTPSourceClock::m_nSystemTimeZero = 0;
//ISysTimer* RTPSourceClock::m_piSysTimer = NULL;

/* -----------------------------------------------------------------------
** Local Object Definitions
** ----------------------------------------------------------------------- */

/* =======================================================================
**                        Class & Function Definitions
** ======================================================================= */
/**
 * @brief This constructor creates an ISysTimer instance and sets the clock.
 * @param piEnv
 * @param result false if unsuccessfull true otherwise
 */
StreamSourceClock::StreamSourceClock(bool& result)
  : m_nSystemTimeZero(0),
    m_LastFetchedUTCTimeInMsec(0),
    m_LastReturnedUTCTimeInMsec(0),
    m_nTickCountStartMsec(0),
    mhTimer(NULL),
    m_pTimerLock(NULL)
{
  //Set system clock offset
  if (MM_Time_GetTime((unsigned long*)&m_nSystemTimeZero) == 0)
  {
    result = true;
  }
  else
  {
    result = false;
  }

  if(result)
  {
      result = (0 == MM_CriticalSection_Create(&m_pTimerLock)) ? true : false;

      //Create timer lock
      if(result)
      {
        //Create timer to query UTC time from timedaemon every HTTP_TIMER_TICK_INTERVAL msecs
        result = (0 == MM_Timer_CreateEx(1, FetchCurrentUTCTime, this, &mhTimer)) ? true : false;
        if (result)
        {
          //Timer setup.
          FetchCurrentUTCTime(this);
          MM_Timer_Start(mhTimer, HTTP_UTC_TIMER_FETCH_INTERVAL);
        }
        else
        {
          QTV_MSG_PRIO( QTVDIAG_HTTP_STREAMING, QTVDIAG_PRIO_ERROR,
            "HTTPSessionInfo Unable to create timer" );
          if(mhTimer)
          {
            MM_Timer_Release(mhTimer);
            mhTimer = NULL;
          }
        }
      }
      else
      {
        QTV_MSG_PRIO( QTVDIAG_HTTP_STREAMING, QTVDIAG_PRIO_ERROR,
          "HTTPSessionInfo Unable to create timer lock" );
      }
    }
  }

/**
 * This destructor releases the ISysTimer interface.
 */
StreamSourceClock::~StreamSourceClock()
{
  if(mhTimer)
  {
    MM_Timer_Stop(mhTimer);
    MM_Timer_Release(mhTimer);
    mhTimer = NULL;
  }
  if(m_pTimerLock)
  {
    MM_CriticalSection_Release(m_pTimerLock);
    m_pTimerLock = NULL;
  }
}

/** @brief This method returns the current value of the play clock in milliseconds.
 *
 * @return Current clock time
 */
uint32 StreamSourceClock::GetTickCount()
{
  uint32 timeNow = 0;
  int64 tickCount = 0;

  //Get the current system clock offset
  if (MM_Time_GetTime((unsigned long*)&timeNow) == 0)
  {
    tickCount = timeNow;
    //Handle wrap-around
    if (timeNow <= m_nSystemTimeZero)
    {
      tickCount += (static_cast<int64>(1) << 32);
    }
    //Compute offset from m_nSystemTimeZero
    tickCount -= m_nSystemTimeZero;
  }
  else
  {
    tickCount = m_nSystemTimeZero;
  }

  return static_cast<uint32>(tickCount);
}

/** @brief Timer expiry callback. Query current time from MM_Time_GetUTCTime.
  *
  * @return -  void
  *
  * FetchCurrentUTCTime() is a timer callback function which is triggered
  * every 1 sec. It fetches and updates anchor UTC time every 1 sec.
  *
  * GetUTCTimeInMsec() uses this anchor UTC time  m_LastFetchedUTCTimeInMsec
  * and elapsed timer to return current UTC time for all internal dash client
  * get UTC time calls.
  *
  */
void StreamSourceClock::FetchCurrentUTCTime(void *arg)
{
  StreamSourceClock* pSelf = (StreamSourceClock*)arg;
  if(pSelf)
  {
    MM_CriticalSection_Enter(pSelf->m_pTimerLock);

    MM_Time_DateTime currUTCTime = {0, 0, 0, 0, 0, 0, 0, 0};
    int ret = MM_Time_GetUTCTime(&currUTCTime);

    if (!ret)
    {
    pSelf->m_LastFetchedUTCTimeInMsec = StreamSourceTimeUtils::ConvertSysTimeToMSecFromEpoch(currUTCTime);
    pSelf->m_nTickCountStartMsec = pSelf->GetTickCount();

    char date_time[40] = {0};

    (void)std_strlprintf(date_time, sizeof(date_time), "%d-%d-%dT%d:%d:%d.%03ldZ",
      currUTCTime.m_nYear,
      currUTCTime.m_nMonth,
      currUTCTime.m_nDay,
      currUTCTime.m_nHour,
      currUTCTime.m_nMinute,
      currUTCTime.m_nSecond,
      currUTCTime.m_nMilliseconds);

    QTV_MSG_PRIO2( QTVDIAG_HTTP_STREAMING, QTVDIAG_PRIO_ERROR,
      "HTTPSessionInfo::FetchCurrentUTCTime from time_daemon  date time %s, UTC time in msec %f",
      date_time,
      pSelf->m_LastFetchedUTCTimeInMsec);
    }
    else
    {
      QTV_MSG_PRIO( QTVDIAG_HTTP_STREAMING, QTVDIAG_PRIO_ERROR,
        "HTTPSessionInfo::FetchCurrentUTCTime MM_Time_GetUTCTime() failed");
    }

    MM_CriticalSection_Leave(pSelf->m_pTimerLock);
  }
}

/** @brief Return current UTC time in milli seconds.
 *
 *   GetUTCTimeInMsec() uses a base m_LastFetchedUTCTimeInMsec and an internal elapsed timer
 *   to calculate current UTC time in msecs
 *
 * @return -  current UTC time in Msec
 */
double StreamSourceClock::GetUTCTimeInMsec()
{
  double currUTCTimeInMsec = 0.0;

    MM_CriticalSection_Enter(m_pTimerLock);

    uint32 nElapsedTime = GetElapsedTime(m_nTickCountStartMsec);
    if(nElapsedTime > 0)
    {
      currUTCTimeInMsec = m_LastFetchedUTCTimeInMsec + (double)nElapsedTime;
      m_LastReturnedUTCTimeInMsec = currUTCTimeInMsec;
    }
    else
    {
      //Can be here only if elapsed time from m_nTickCountStartMsec wraps around
      //Do not have to worry about this use case as it happens only after 2^32 msec
      //There also can be momentary glitch (very uncommon) where system time rolls
      //back as stated in GetElapsedTime() in which case elapsed time gives zero.
      //In such case return same last UTC time value that was returned.
      currUTCTimeInMsec = m_LastReturnedUTCTimeInMsec;
    }

    MM_CriticalSection_Leave(m_pTimerLock);

  return currUTCTimeInMsec;
}

/** @brief This method returns the elapsed time from a specified start time.
 *
 * @param[in] startTime - Start time
 * @return Elapsed time since the start time
 */
uint32 StreamSourceClock::GetElapsedTime
(
 const uint32 startTime
)
{
  uint32 timeDiff = 0;

  uint32 currTime = GetTickCount();

  if (currTime >= startTime)
  {
    timeDiff = currTime - startTime;
  }
  else
  {
    //Don't have to worry about currTime wraparound (since session start).
    //Can be here if system time rolls back say due to NTP sync correction
    //(e.g. device roams from LTE->CDMA system), in which case caller will
    //just ignore this sample
    QTV_MSG_PRIO2( QTVDIAG_HTTP_STREAMING, QTVDIAG_PRIO_ERROR,
      "Error: Incorrect startTime %lu/%lu start/curr",
      startTime, currTime );
  }

  return timeDiff;
}


