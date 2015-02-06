(ns clonsq.protocol
  (:require [clojure.string :as string]
            [gloss.core :refer [defcodec enum finite-frame header ordered-map
                                string]]
            [gloss.data.bytes :refer [dup-bytes]]
            [gloss.io :as io :refer [contiguous to-buf-seq]]))

(defcodec frame-types (enum :int32 {:response 0 :error 1 :message 2}))

(defcodec response (ordered-map :type :response
                                :body (string :utf8)))

(defcodec error (ordered-map :type :error
                             :body (string :utf8)))

(def identity-codec
  (reify
    gloss.core.protocols.Reader
    (read-bytes [_ b]
      [true (contiguous (dup-bytes b)) nil])
    gloss.core.protocols.Writer
    (sizeof [_]
      nil)
    (write-bytes [_ _ b]
      (-> b to-buf-seq dup-bytes))))

(defcodec message (ordered-map :type :message
                               :timestamp :int64
                               :attempts :uint16
                               :id (string :ascii :length 16)
                               :body identity-codec))

; http://nsq.io/clients/tcp_protocol_spec.html
(defcodec main
  (finite-frame :int32
                (header frame-types
                        {:response response
                         :error error
                         :message message}
                        :type)))

(defn decode-stream [stream]
  (io/decode-stream stream main))

(defmulti encode (fn [cmd & more] cmd))
(defmethod encode :nop [_] "NOP\n")
(defmethod encode :close [_] (str "CLS" "\n"))
(defmethod encode :fin [_ id] (str "FIN " id "\n"))
(defmethod encode :req [_ id] (str "REQ " id "\n"))
(defmethod encode :magic-id [_] "  V2")
(defmethod encode :subscribe [_ topic channel] (string/join " " ["SUB" topic channel "\n"]))
(defmethod encode :rdy [_ n] (string/join " " ["RDY" n "\n"]))
