def get_file_extensions():
    file_extensions = [
        ".png",
        ".gif",
        ".jpg",
        ".jpeg",
        ".tiff",
        ".tif",
        ".xcf",
        ".mid",
        ".ogg",
        ".ogv",
        ".svg",
        ".djvu",
        ".oga",
        ".flac",
        ".opus",
        ".wav",
        ".webm",
        ".ico",
        ".txt"
    ]
    
    return file_extensions


def get_black_list():
    black_list = [
        "get_definition_talk:",
        "draft:",
        "user_talk:",
        "template_talk:",
        "mediawiki_talk:",
        "module_talk:",
        "file_talk:",
        "book:",
        "gadget_definition:",
        "user:",
        "wikipedia_talk:",
        "special:",
        "portal_talk:",
        "module:",
        "category:",
        "timedtext:",
        "template:",
        "file:",
        "gadget_talk:",
        "gadget:",
        "talk:",
        "help_talk:",
        "category_talk:",
        "mediawiki:",
        "book_talk:",
        "portal:",
        "timedtext_talk:",
        "wikipedia:",
        "help:",
        "draft_talk:",
        "education_program_talk:",
        "education_program:"
    ]

    return black_list


def get_special_pages():
    special_pages = [
        '404.php',
        'Main_Page',
        '-'
    ]

    return special_pages


def decode(encoded):
    def get_hex_value(b):
        if '0' <= b <= '9':
            return chr(ord(b) - 0x30)
        elif 'A' <= b <= 'F':
            return chr(ord(b) - 0x37)
        elif 'a' <= b <= 'f':
            return chr(ord(b) - 0x57)
        return None

    if encoded is None:
        return None
    encoded_chars = encoded
    encoded_length = len(encoded_chars)
    decoded_chars = ''
    encoded_idx = 0
    while encoded_idx < encoded_length:
        if encoded_chars[encoded_idx] == '%' and encoded_idx + 2 < encoded_length and get_hex_value(encoded_chars[encoded_idx + 1]) and get_hex_value(encoded_chars[encoded_idx + 2]):
            #  current character is % char
            value1 = get_hex_value(encoded_chars[encoded_idx + 1])
            value2 = get_hex_value(encoded_chars[encoded_idx + 2])
            decoded_chars += chr((ord(value1) << 4) + ord(value2))
            encoded_idx += 2
        else:
            decoded_chars += chr(encoded_chars[encoded_idx])
        encoded_idx += 1
    return str(decoded_chars)
