import axios from "axios";

export const api = axios.create({
  baseURL: "/api",
  timeout: 15000,
});

export const unwrap = async (promise) => {
  const { data } = await promise;
  return data;
};
