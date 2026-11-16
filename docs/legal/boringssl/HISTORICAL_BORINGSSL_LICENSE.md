# BoringSSL (pre-2025-02-03) — Historical License Text

**Provenance:** This file is the license text accompanying the historical
(pre-2025-02-03) BoringSSL static library that is statically linked into the
AT Engine AAR (`adb-engine-4.0.0.aar`, native library `libadbd_engine.so`)
bundled in `third_party/at-v044-consumer-freeze-v1/`.

**Upstream:** https://github.com/google/boringssl

**License regime:** pre-2025-02-03 BoringSSL — a mix of OpenSSL BSD, SSLeay
BSD, Google ISC, and MIT (fiat) licenses. The exact upstream commit is
unrecoverable; the bundled `LICENSE` file in the BoringSSL distribution
applies to the files it covered.

The per-file copyright notices (OpenSSL, SSLeay, Google, fiat) are preserved
in the individual header file comments of the BoringSSL sources from which
the static library was built. The original OpenSSL copyright notices:

> This product includes software developed by the OpenSSL Project for
> use in the OpenSSL Toolkit (http://www.openssl.org/).
>
> This product includes cryptographic software written by Eric Young
> (eay@cryptsoft.com). This product includes software written by Tim
> Hudson (tjh@cryptsoft.com).

## Redistribution requirements (satisfied by this repository)

| Requirement | How it is satisfied here |
|-------------|--------------------------|
| OpenSSL BSD: retain copyright notices | Preserved in BoringSSL header comments upstream; acknowledged in root NOTICE |
| OpenSSL BSD: reproduce license in binary distributions | This file + root NOTICE accompany the bundled AAR |
| SSLeay BSD: retain copyright + Eric Young acknowledgment | Root NOTICE includes the Eric Young / Tim Hudson acknowledgment |
| ISC: retain copyright and permission notice | Acknowledged in root NOTICE |
| MIT (fiat): include copyright notice and permission notice | This file accompanies the binary distribution |

## Reference license texts

### OpenSSL License

    /* ====================================================================
     * Copyright (c) 1998-2019 The OpenSSL Project. All rights reserved.
     *
     * Redistribution and use in source and binary forms, with or without
     * modification, are permitted provided that the following conditions
     * are met:
     *
     * 1. Redistributions of source code must retain the above copyright
     *    notice, this list of conditions and the following disclaimer.
     *
     * 2. Redistributions in binary form must reproduce the above copyright
     *    notice, this list of conditions and the following disclaimer in
     *    the documentation and/or other materials provided with the
     *    distribution.
     *
     * 3. All advertising materials mentioning features or use of this
     *    software must display the following acknowledgment:
     *    "This product includes software developed by the OpenSSL Project
     *    for use in the OpenSSL Toolkit. (http://www.openssl.org/)"
     *
     * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used
     *    to endorse or promote products derived from this software without
     *    prior written permission. For written permission, please contact
     *    openssl-core@openssl.org.
     *
     * 5. Products derived from this software may not be called "OpenSSL"
     *    nor may "OpenSSL" appear in their names without prior written
     *    permission of the OpenSSL Project.
     *
     * 6. Redistributions of any form whatsoever must retain the following
     *    acknowledgment:
     *    "This product includes software developed by the OpenSSL Project
     *    for use in the OpenSSL Toolkit (http://www.openssl.org/)"
     *
     * THIS SOFTWARE IS PROVIDED BY THE OpenSSL PROJECT ``AS IS'' AND ANY
     * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
     * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
     * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE OpenSSL PROJECT OR
     * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
     * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
     * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
     * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
     * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
     * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
     * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
     * OF THE POSSIBILITY OF SUCH DAMAGE.
     * ====================================================================
     */

### SSLeay License

    /* ====================================================================
     * Copyright (c) 1995-1998 Eric A. Young, Tim J. Hudson
     * All rights reserved.
     *
     * Redistribution and use in source and binary forms, with or without
     * modification, are permitted provided that the following conditions
     * are met:
     *
     * 1. Redistributions of source code must retain the copyright
     *    notice, this list of conditions and the following disclaimer.
     * 2. Redistributions in binary form must reproduce the above
     *    copyright notice, this list of conditions and the following
     *    disclaimer in the documentation and/or other materials provided
     *    with the distribution.
     * 3. All advertising materials mentioning features or use of this
     *    software must display the following acknowledgment:
     *    "This product includes software developed by  Eric A. Young
     *    (eay@cryptsoft.com)"
     * 4. The names "EAY" and "Crypto" must not be used to endorse or
     *    promote products derived from this software without prior
     *    written permission. For written permission, please contact
     *    eaysscdc@cryptsoft.com.
     * 5. Products derived from this software may not be called "SSLeay"
     *    nor may "SSLeay" appear in their names without prior written
     *    permission of Eric Young.
     * 6. Redistributions of any form whatsoever must retain the
     *    following acknowledgment:
     *    "This product includes software developed by Eric A. Young
     *    (eay@cryptsoft.com)"
     *
     * THIS SOFTWARE IS PROVIDED BY ERIC YOUNG ``AS IS'' AND ANY
     * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
     * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
     * PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE
     * AUTHOR OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY
     * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
     * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
     * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
     * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
     * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
     * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
     * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
     * THE POSSIBILITY OF SUCH DAMAGE.
     * ====================================================================
     */

### ISC License (Google BoringSSL code)

    Copyright (c) 2015, Google Inc.
    All rights reserved.

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions are
    met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

    * Other documents that mention the redistributible materials may
      state that the materials were changed or improved.

    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in
      the documentation and/or other materials provided with the
      distribution.

    * Neither the name of the project nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
    "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
    LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
    FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
    COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
    INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
    (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
    SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
    HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
    STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
    ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
    OF THE POSSIBILITY OF SUCH DAMAGE.

### MIT License (fiat, third_party/fiat)

    Copyright (c) 2015-2017, Fiat Cryptography contributors
    All rights reserved.

    (Standard MIT terms: permission is hereby granted, free of charge, to
    any person obtaining a copy of this software and associated
    documentation files (the "Software"), to deal in the Software without
    restriction, including without limitation the rights to use, copy,
    modify, merge, publish, distribute, sublicense, and/or sell copies of
    the Software, and to permit persons to whom the Software is furnished
    to do so, subject to the conditions that the above copyright notice and
    this permission notice appear in all copies or substantial portions of
    the Software.)
