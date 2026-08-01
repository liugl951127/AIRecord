import request from '@/utils/request'

// 录制报告导出
export const exportReportHtml = (sessionId) => {
  return request.get(`/report/${sessionId}/html`, { responseType: 'blob' })
}
export const viewReport = (sessionId) => {
  return `/api/report/${sessionId}/view`
}
export const exportReportPdf = (sessionId) => {
  return request.get(`/report/${sessionId}/pdf`, { responseType: 'blob' })
}
