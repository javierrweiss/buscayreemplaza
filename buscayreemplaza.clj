#!/usr/bin/env bb

(require '[clojure.tools.cli :refer [parse-opts]]
         '[clojure.java.io :as io]
         '[clojure.string :as s]
         '[babashka.fs :as fs]
         '[clojure.stacktrace :as st])

(def ayuda "Busca y reemplaza  \n
            Busca y reemplaza es una herramienta de linea de comandos pensada para hacer pequenas modificaciones por lotes en tu sistema de archivos. \n
            Es necesario tener instalado babashka https://github.com/babashka/babashka para poder ejecutarla. 
            Uso: 
            bb -f buscayreemplaza.clj -e <expresion> <reemplazo> <archivo> <ruta> 
            bb buscayreemplaza.clj -e <expresion> <reemplazo> <archivo> <ruta>
            bb buscayreemplaza.clj -e <expresion> <reemplazo> <archivo> <ruta> -b <rutas-excluidas>")

(def opciones [["-h" "--help"]
               ["-e" "--exec EXPRESSION REPLACEMENT FILE PATH"]
               ["-b" "--blacklist PATHS" "Excluye las rutas especificadas"]
               ["-p" "--preview FILE DIR" "Imprime lista de archivos filtrados con el criterio de busqueda"]])

(defn- glob?
  [input]
  (if (re-matches #"\*\.\w+|\*\.\*|\w+\.\*|\*\.\{\w+\,\w+\}|\w+\.\{\w+\,\w+\}|\w+\.\?|\/\w+\/\*\/\*|\/\w+\/\*\/|\/\w+\/\*\*|\w\:\\\*|\[\w+\]|\[\w\-\w\]|\[\!\w\-\w\]" input)
    true
    false))

(defn- filter-files
  ([file dir]
   (try
    (if (glob? file)
      (fs/list-dir dir file)
      (eduction (filter #(-> (.getName %) (.contains file))) (file-seq (io/file dir)))) 
     (catch Exception e (st/root-cause e))))
  ([file dir & filters]
  (try
    (if (glob? file)
      (remove #(some true? (map (fn [fltrexp] (.contains (.toString %) fltrexp)) (flatten filters))) (fs/list-dir dir file))
      (eduction (filter #(-> (.getName %) (.contains file)))
                (remove #(some true? (map (fn [fltrexp] (.contains (.getName %) fltrexp)) (flatten filters))))
                (file-seq (io/file dir))))
    (catch Exception e (st/root-cause e)))))
 
(defn- write-file
  [file text]
  (try
    (when (nil? (spit (.getAbsolutePath file) text))
      "Exito")
    (catch Exception e (st/root-cause e))))

(defn- replace-text
  [file dir expression replacement & filters]
  (let [filtered-files (if (nil? filters) (filter-files file dir) (filter-files file dir filters))
        processed (for [archivo filtered-files :let [text (slurp (.getAbsolutePath archivo))
                                                     pattrn (re-pattern expression)
                                                     output (s/replace text pattrn replacement)]]
                    (let [except (write-file archivo output)]
                      (when (instance? java.lang.Exception except)
                        (str "\n Hubo un problema con el archivo \n" archivo " Excepcion: " except))))] 
    (if (some #(not (nil? %)) processed)
      processed
      "Exito")))
 
(println (let [{:keys [options arguments summary]} (parse-opts *command-line-args* opciones)
               {:keys [help exec blacklist preview]} options
               [replcmnt arch pth & filtrs] arguments]
           #_(println (str "Ayuda: " help "\nEjecucion: " exec "\nLista negra: " blacklist "\nPreview: " preview "\nArgumentos: " arguments
                         "\nReemplazo " replcmnt "\nArchivo " arch "\nRuta " pth "\nLista negra: " blacklist "\nFiltro " filtrs))
           (cond
             help summary
             preview (map #(-> % str symbol) (filter-files preview (first arguments)))
             blacklist (if (nil? filtrs) (replace-text arch pth exec replcmnt blacklist) (replace-text arch pth exec replcmnt blacklist filtrs))
             exec (replace-text arch pth exec replcmnt)
             :else (str "Opcion invalida\n" ayuda))))


(comment
  ;;Replacing 
  (slurp "c:/Users/jrivero/servoy_workspace6/ProbandoLaPrueba.txt")
  (replace-text "ProbandoLaPrueba" "c:/Users/jrivero/servoy_workspace6" "jartera" "fiaca")
  (replace-text "rootmetadata.obj" "c://Users//jrivero//servoy_workspace6//AbmConsentimientos" "44" "48" ".svn")
  (replace-text "rootmetadata.obj" "c://Users//jrivero//servoy_workspace6//AbmConsentimientos" "48" "38")
  
  ;;Filtering
  (def dir1 "c://Users//jrivero//servoy_workspace6//AbmConsentimientos")
  (def dir "c://Users//jrivero//servoy_workspace6") 
  (def arch1 "rootmetadata.obj") 
  (def archivo (filter-files arch1 dir1))
  (filter-files "tbc_medicos_personal.json" "c:\\Users\\jrivero")
  (filter-files "*.json" "c:\\Users\\jrivero")
  (filter-files "*.obj" "c:\\Users\\jrivero\\servoy_workspace6//AbmConsentimientos" ".svn" "forms") 
  (filter-files "*.js" "c:\\Users\\jrivero\\servoy_workspace6\\AsiUtiTrs" ".svn" "forms")
  (filter-files "*.{json,txt}" "c:\\Users\\jrivero") 
  (filter-files "tbc_medicos_personal.json" "c:\\Users\\jrivero" "AppData")
  (filter-files "rootmetadata.obj" "c://Users//jrivero//servoy_workspace6//AbmConsentimientos")
  (filter-files "rootmetadata" "c://Users//jrivero//servoy_workspace6//AbmConsentimientos") 
  (filter-files "rootmetadata" "c://Users//jrivero//servoy_workspace6//")
  (def tst
    (filter-files "rootmetadata.obj" "c://Users//jrivero//servoy_workspace6//AbmConsentimientos" ".svn" "forms"))
  (filter #(-> (.getName %) (.contains "rootmetadata")) (file-seq (io/file dir)))
  
  ;;Writing
  (write-file (io/file "Probandowritefile.txt") "Si puedes leer esto, write-file trabaja correctamente")
  (write-file (io/file "Probandowritef.txt") "Si puedes leer esto, write-file trabaja correctamente")
  (instance? java.lang.Exception (write-file "Probandowritef.txt" "Si puedes leer esto, write-file trabaja correctamente"))
  (slurp "Probandowritefile.txt")
  (slurp "Probandowritef.txt")
  
  ;;Parse-opts
  (let [{:keys [options arguments summary]} {:options nil :arguments ["Exito" "Bubba" "ProbandoLaPrueba" "c:/Users/jrivero/servoy_workspace6"] :summary ayuda}]
    arguments)
  (slurp "c://Users//jrivero//servoy_workspace6//ProbandoLaPrueba.txt") 
  (def file "ProbandoLaPrueba.txt")
  (def dir "c:\\Users\\jrivero\\servoy_workspace6\\")
  (def expression "Bubba")
  (def replacement "Exito")
  (println (parse-opts *command-line-args* opciones))

  ;;FS 
  (fs/list-dir  "c:\\Users\\jrivero" "*.js")(fs/list-dir  "c:\\Users\\jrivero" "*.js")
  (fs/glob dir "**.obj")
  (fs/glob "c:\\Users\\jrivero" "*.txt")
  (fs/list-dir "c:\\Users\\jrivero" "*.txt")
  (fs/list-dirs "c:\\Users\\jrivero" "*.txt")
  (fs/glob "c:\\Users\\jrivero" "ProbandoLaPrueba.txt")
  (fs/list-dir "c:\\Users\\jrivero" "ProbandoLaPrueba.txt")
  (fs/glob (fs/list-dir "c:\\Users\\jrivero") "ProbandoLaPrueba.txt") 
  (fs/regular-file? "c://Users//jrivero//servoy_workspace6//ProbandoLaPrueba.txt")
  (fs/regular-file? (io/file "c://Users//jrivero//servoy_workspace6//ProbandoLaPrueba.txt")) 
  (fs/regular-file? "*.*") 
  (def archivos
    (remove #(fs/directory? %) (fs/list-dir "c:\\Users\\jrivero"))) 
  (fs/list-dir "c:\\Users\\jrivero" "*.txt")
  (fs/list-dir "c:\\Users\\jrivero" "AUX-3056-111-1") 
  (fs/match "." "regex:.*\\.txt" {:recursive false})
  (fs/match "." "glob:*.txt" {:recursive false})
  (clojure.pprint/pprint (file-seq (io/file "c:\\Users\\jrivero")))

  ;;Varios
  (.getAbsolutePath (first (eduction (filter #(-> (.getName %) (.contains "rootmetadata"))) (remove #(-> (.getName %) (.contains ".svn")))
                                     (file-seq (io/file "c:\\Users\\jrivero\\servoy_workspace6")))))
 (.getAbsolutePath (first (sequence (comp (filter #(-> (.getName %) (.contains "rootmetadata"))) (remove #(-> (.getName %) (.contains ".svn"))))
                                    (file-seq (io/file "c:\\Users\\jrivero\\servoy_workspace6"))))) 
  (eduction (filter #(-> (.getName %) (.contains "rootmetadata")))
            (remove #(some true? (map (fn [fltrexp] (.contains (.getName %) fltrexp)) (vector ".svn" "forms"))))
            (file-seq (io/file "c:\\Users\\jrivero\\servoy_workspace6")))
  (remove #(-> (.getName %) (.contains ".svn")) (file-seq (io/file "c:\\Users\\jrivero\\servoy_workspace6\\AsiUtiTrs"))) 
  (remove #(some true? (map (fn [fltrexp] (.contains (.toString %) fltrexp)) [".svn"])) (fs/list-dir "c:\\Users\\jrivero\\servoy_workspace6\\AbmConsentimientos"))
 (remove #(some true? (map (fn [fltrexp] (.contains (.toString %) fltrexp)) [".svn"])) (fs/list-dir "c:\\Users\\jrivero\\servoy_workspace6\\AbmConsentimientos" "*.obj"))  
  (map #(.toString %) (fs/list-dir "c:\\Users\\jrivero\\servoy_workspace6\\AbmConsentimientos"))
  (def t ["Esto es un texto muy largo y copioso y no quiero leer tanto"
          "Esta es una oracion que dice largo y copioso"
          "Esta es una oracion pura y cristiana"]) 
  (def l ["largo" "copioso" "tanto"]) 
  (remove #(some true? (map (fn [x] (.contains % x)) l)) t)
  (some true? (map (fn [x] (.contains (second t) x)) l))
  (.contains (second t) (nth l 2)) 
 (re-matches #"\d{1,6}" "232322")
 (map #(glob? %) ["datar.*" "*.*" "*.{java,class}" "claseX.{java,class}" "foo.?" "/home/*/*" "/home/**" "C:\\*" "[a-z]" "[!a-c]"])
  (def resultados (list "Exito" "Exito" "Exito" "Exito" "Exito" "Hubo un problema con el archivo"))
 (some #(.equalsIgnoreCase % "Hubo un problema con el archivo") resultados)
  ,)  
 

