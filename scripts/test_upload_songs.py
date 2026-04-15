import unittest
from unittest.mock import patch, MagicMock
import os
import sys

# Add scripts directory to path to import upload_songs
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from upload_songs import clean_filename, upload_file, upload_songs

class TestUploadSongs(unittest.TestCase):

    def test_clean_filename(self):
        self.assertEqual(clean_filename("song_name.mp3"), "Song Name")
        self.assertEqual(clean_filename("another-song-title.mp3"), "Another Song Title")
        self.assertEqual(clean_filename("mixed_separators-style.mp3"), "Mixed Separators Style")
        self.assertEqual(clean_filename("already Correct Capitalization.mp3"), "Already Correct Capitalization")
        self.assertEqual(clean_filename("multiple___underscores.mp3"), "Multiple   Underscores")

    @patch('requests.post')
    @patch('builtins.open', new_callable=unittest.mock.mock_open, read_data=b'fake mp3 content')
    @patch('os.path.isfile')
    def test_upload_file_success(self, mock_isfile, mock_open, mock_post):
        mock_isfile.return_value = True
        mock_response = MagicMock()
        mock_response.status_code = 201
        mock_post.return_value = mock_response

        # Redirect stdout to capture print statements
        with patch('sys.stdout', new=MagicMock()):
            upload_file("test_song.mp3", "Test Artist", "http://api/upload", "test_key")

        mock_post.assert_called_once()
        args, kwargs = mock_post.call_args
        self.assertEqual(args[0], "http://api/upload")
        self.assertEqual(kwargs['headers'], {'X-API-KEY': 'test_key'})
        self.assertEqual(kwargs['data'], {'name': 'Test Song', 'artist': 'Test Artist'})
        # Check if file was sent
        self.assertIn('file', kwargs['files'])
        self.assertEqual(kwargs['files']['file'][0], 'test_song.mp3')

    @patch('requests.post')
    @patch('builtins.open', new_callable=unittest.mock.mock_open, read_data=b'fake mp3 content')
    @patch('os.path.isfile')
    def test_upload_file_failure(self, mock_isfile, mock_open, mock_post):
        mock_isfile.return_value = True
        mock_response = MagicMock()
        mock_response.status_code = 400
        mock_response.text = "Bad Request"
        mock_post.return_value = mock_response

        with patch('sys.stdout', new=MagicMock()):
            upload_file("test_song.mp3", "Test Artist", "http://api/upload", "test_key")

        mock_post.assert_called_once()

    @patch('os.path.isfile')
    @patch('upload_songs.print')
    def test_upload_file_not_exists(self, mock_print, mock_isfile):
        mock_isfile.return_value = False
        upload_file("non_existent.mp3", "Artist", "url", "key")
        mock_print.assert_any_call("Error: File non_existent.mp3 does not exist.")

    @patch('upload_songs.print')
    def test_upload_file_not_mp3(self, mock_print):
        # We need to ensure that the file exists to reach the extension check
        # Actually in upload_songs.py:
        # if not os.path.isfile(file_path): ... return
        # filename = os.path.basename(file_path)
        # if not filename.lower().endswith('.mp3'): ... return
        with patch('os.path.isfile', return_value=True):
            upload_file("not_a_song.txt", "Artist", "url", "key")
            mock_print.assert_any_call("Skipping not_a_song.txt: Not an MP3 file.")

    @patch('upload_songs.upload_file')
    @patch('os.path.isfile')
    @patch('os.path.isdir')
    def test_upload_songs_file(self, mock_isdir, mock_isfile, mock_upload_file):
        mock_isfile.return_value = True
        mock_isdir.return_value = False
        
        upload_songs("song.mp3", "Artist", "url", "key")
        mock_upload_file.assert_called_once_with("song.mp3", "Artist", "url", "key")

    @patch('upload_songs.upload_file')
    @patch('os.path.isfile')
    @patch('os.path.isdir')
    @patch('os.listdir')
    def test_upload_songs_dir(self, mock_listdir, mock_isdir, mock_isfile, mock_upload_file):
        mock_isfile.return_value = False
        mock_isdir.return_value = True
        mock_listdir.return_value = ["song1.mp3", "song2.mp3", "not_song.txt"]
        
        upload_songs("my_dir", "Artist", "url", "key")
        
        self.assertEqual(mock_upload_file.call_count, 2)
        mock_upload_file.assert_any_call("my_dir/song1.mp3", "Artist", "url", "key")
        mock_upload_file.assert_any_call("my_dir/song2.mp3", "Artist", "url", "key")

if __name__ == '__main__':
    unittest.main()
