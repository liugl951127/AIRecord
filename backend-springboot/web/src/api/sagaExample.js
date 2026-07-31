import axios from '@/utils/request'

export function submitOrder(data) {
  return axios.post('/saga-example/submit', data)
}

export function submitOrderFailDemo(data) {
  return axios.post('/saga-example/submit-fail-demo', data)
}
