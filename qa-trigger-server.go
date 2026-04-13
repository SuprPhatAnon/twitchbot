package main

import (
	"fmt"
	"log"
	"net/http"
	"os/exec"
	"strings"
)

func main() {
	http.HandleFunc("/trigger", func(w http.ResponseWriter, r *http.Request) {
		event := r.URL.Query().Get("event")
		if event == "" {
			http.Error(w, "event parameter is required", http.StatusBadRequest)
			return
		}

		args := []string{"event", "trigger", event, "--transport", "websocket"}
		
		// Add any other params from query
		for key, values := range r.URL.Query() {
			if key == "event" {
				continue
			}
			for _, val := range values {
				args = append(args, "--"+key, val)
			}
		}

		log.Printf("Executing: twitch %s", strings.Join(args, " "))
		cmd := exec.Command("twitch", args...)
		output, err := cmd.CombinedOutput()
		if err != nil {
			log.Printf("Error: %v, Output: %s", err, string(output))
			http.Error(w, fmt.Sprintf("Error: %v\nOutput: %s", err, string(output)), http.StatusInternalServerError)
			return
		}

		fmt.Fprintf(w, "Success:\n%s", string(output))
	})

	log.Println("QA Trigger Server starting on :8082...")
	if err := http.ListenAndServe(":8082", nil); err != nil {
		log.Fatal(err)
	}
}
