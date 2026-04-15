import os
import requests
import argparse
import re

def clean_filename(filename):
    # Remove extension
    name_without_ext = os.path.splitext(filename)[0]
    
    # Replace _ and - with space
    cleaned_name = re.sub(r'[_-]', ' ', name_without_ext)
    
    # Proper capitalization (Title Case)
    # Using a simple title() might not be perfect, but it's a good start.
    # We'll use a more robust approach to handle words like "the", "in", etc. if needed,
    # but the requirement is "proper capitalization".
    return cleaned_name.title()

def upload_file(file_path, artist, api_url, api_key):
    if not os.path.isfile(file_path):
        print(f"Error: File {file_path} does not exist.")
        return

    filename = os.path.basename(file_path)
    if not filename.lower().endswith('.mp3'):
        print(f"Skipping {filename}: Not an MP3 file.")
        return

    song_name = clean_filename(filename)
    print(f"Uploading: {song_name} by {artist} ({filename})...")
    
    try:
        with open(file_path, 'rb') as f:
            files = {'file': (filename, f, 'audio/mpeg')}
            data = {'name': song_name, 'artist': artist}
            headers = {'X-API-KEY': api_key}
            
            response = requests.post(
                api_url, 
                files=files, 
                data=data, 
                headers=headers
            )
            
        if response.status_code == 201:
            print(f"Successfully uploaded: {song_name}")
        else:
            print(f"Failed to upload {song_name}. Status: {response.status_code}, Response: {response.text}")
    except Exception as e:
        print(f"Error uploading {song_name}: {e}")

def upload_songs(path, artist, api_url, api_key):
    if os.path.isfile(path):
        upload_file(path, artist, api_url, api_key)
    elif os.path.isdir(path):
        for filename in os.listdir(path):
            if filename.lower().endswith('.mp3'):
                file_path = os.path.join(path, filename)
                upload_file(file_path, artist, api_url, api_key)
    else:
        print(f"Error: {path} is not a valid file or directory.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Upload MP3 file(s) from a file or directory to the Twitch Song Overlay Bot.")
    parser.add_argument("path", help="Path to an MP3 file or a directory containing MP3 files.")
    parser.add_argument("--artist", default="Unknown Artist", help="Default artist for the songs (default: Unknown Artist).")
    parser.add_argument("--url", default="http://localhost:8080/api/songs/upload", help="The API upload endpoint URL (default: http://localhost:8080/api/songs/upload).")
    parser.add_argument("--api-key", required=True, help="API Key for authentication.")

    args = parser.parse_args()

    upload_songs(args.path, args.artist, args.url, args.api_key)
